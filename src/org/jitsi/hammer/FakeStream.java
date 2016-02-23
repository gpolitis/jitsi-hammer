/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.hammer;

import io.pkts.PacketHandler;
import io.pkts.Pcap;
import io.pkts.protocol.Protocol;
import io.pkts.protocol.Protocol;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import org.jitsi.hammer.extension.*;
import org.jitsi.hammer.utils.PcapChooser;
import org.jitsi.impl.neomedia.RTPPacketPredicate;
import org.jitsi.impl.neomedia.RawPacket;
import org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile.RTPThrottle;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author George Politis
 */
public class FakeStream
{
    /**
     * The <tt>Logger</tt> used by <tt>RtpdumpStream</tt> and its instances
     * for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(FakeStream.class);

    /**
     *
     */
    private final MediaStream stream;

    /**
     *
     */
    private final Pcap pcap;

    /**
     *
     */
    private final long[] ssrcs;

    /**
     *
     */
    private boolean closing = false;

    /**
     *
     */
    private Thread injectThread;

    /**
     *
     */
    private MediaFormat format;

    /**
     * Ctor.
     *
     * @param pcapChooser
     * @param stream
     */
    public FakeStream(PcapChooser pcapChooser, MediaStream stream)
    {
        this.stream = stream;
        if (!isAudio())
        {
            this.pcap = pcapChooser.getVideoPcap();
            this.ssrcs = pcapChooser.getVideoSsrcs();
        }
        else
        {
            this.pcap = null;
            this.ssrcs = null;
        }
    }

    public boolean isAudio()
    {
        return stream instanceof AudioMediaStream;
    }

    public long getLocalSourceID()
    {
        return stream.getLocalSourceID();
    }

    public MediaStreamStats getMediaStreamStats()
    {
        return stream.getMediaStreamStats();
    }

    public void close()
    {
        closing = true;
        if (injectThread != null)
        {
            injectThread.interrupt();
            try
            {
                injectThread.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        stream.close();
    }

    public void setDevice(MediaDevice device)
    {
        stream.setDevice(device);
    }

    public void setFormat(MediaFormat format)
    {
        this.format = format;
        stream.setFormat(format);
    }

    public void setName(String name)
    {
        stream.setName(name);
    }

    public void setRTPTranslator(RTPTranslator rtpTranslator)
    {
        stream.setRTPTranslator(rtpTranslator);
    }

    public void setDirection(MediaDirection direction)
    {
        stream.setDirection(direction);
    }

    public void addDynamicRTPPayloadType(Byte payloadType, MediaFormat format)
    {
        stream.addDynamicRTPPayloadType(payloadType, format);
    }

    public void addRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        stream.addRTPExtension(extensionID, rtpExtension);
    }

    public void addSSRCToContent(ContentPacketExtension content)
    {
        RtpDescriptionPacketExtension
                description = content.getFirstChildOfType(
                RtpDescriptionPacketExtension.class);

        if (ssrcs == null || ssrcs.length == 0)
        {
            long ssrc = stream.getLocalSourceID();

            description.setSsrc(String.valueOf(ssrc));
            MediaService mediaService = LibJitsi.getMediaService();
            String msLabel = UUID.randomUUID().toString();
            String label = UUID.randomUUID().toString();

            SourcePacketExtension sourcePacketExtension =
                    new SourcePacketExtension();
            SsrcPacketExtension ssrcPacketExtension =
                    new SsrcPacketExtension();


            sourcePacketExtension.setSSRC(ssrc);
            sourcePacketExtension.addChildExtension(
                    new ParameterPacketExtension("cname",
                            mediaService.getRtpCname()));
            sourcePacketExtension.addChildExtension(
                    new ParameterPacketExtension("msid", msLabel + " " + label));
            sourcePacketExtension.addChildExtension(
                    new ParameterPacketExtension("mslabel", msLabel));
            sourcePacketExtension.addChildExtension(
                    new ParameterPacketExtension("label", label));
            description.addChildExtension(sourcePacketExtension);


            ssrcPacketExtension.setSsrc(String.valueOf(ssrc));
            ssrcPacketExtension.setCname(mediaService.getRtpCname());
            ssrcPacketExtension.setMsid(msLabel + " " + label);
            ssrcPacketExtension.setMslabel(msLabel);
            ssrcPacketExtension.setLabel(label);
            description.addChildExtension(ssrcPacketExtension);
        }
        else
        {

            MediaService mediaService = LibJitsi.getMediaService();
            String msLabel = UUID.randomUUID().toString();
            String label = UUID.randomUUID().toString();

            List<SourcePacketExtension> sources = new ArrayList<>();
            for (long ssrc : ssrcs)
            {
                SourcePacketExtension sourcePacketExtension =
                        new SourcePacketExtension();

                sourcePacketExtension.setSSRC(ssrc);
                sourcePacketExtension.addChildExtension(
                        new ParameterPacketExtension("cname",
                                mediaService.getRtpCname()));
                sourcePacketExtension.addChildExtension(
                        new ParameterPacketExtension("msid", msLabel + " " + label));
                sourcePacketExtension.addChildExtension(
                        new ParameterPacketExtension("mslabel", msLabel));
                sourcePacketExtension.addChildExtension(
                        new ParameterPacketExtension("label", label));
                sources.add(sourcePacketExtension);
                description.addChildExtension(sourcePacketExtension);
            }

            SourceGroupPacketExtension sourceGroupPacketExtension =
                    SourceGroupPacketExtension.createSimulcastGroup();
            sourceGroupPacketExtension.addSources(sources);
            description.addChildExtension(sourceGroupPacketExtension);
        }
    }

    public SrtpControl getSrtpControl()
    {
        return stream.getSrtpControl();
    }

    class PacketEmitter extends Thread
    {
        final private RTPThrottle rtpThrottle;

        final BlockingQueue<RawPacket> queue;

        PacketEmitter()
        {
            queue = new LinkedBlockingQueue<>(100);
            rtpThrottle = new RTPThrottle(90000);
        }

        public void run()
        {
            while (true)
            {
                RawPacket next = null;

                try
                {
                    next = queue.take();
                }
                catch (InterruptedException e)
                {
                }

                rtpThrottle.throttle(next);

                try
                {
                    boolean data =  RTPPacketPredicate.INSTANCE.test(next);
                    logger.debug(data
                            ? "Injecting RTP ssrc=" + next.getSSRCAsLong()
                            + ", seq=" + next.getSequenceNumber() +
                            ", ts=" + next.getTimestamp()
                            : "Injecting RTCP.");
                    stream.injectPacket(next,data, null);
                }
                catch (TransmissionFailedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }


    public void start()
    {
        stream.start();

        if (pcap == null)
        {
            return;
        }

        final Map<Long, PacketEmitter> emitterMap = new HashMap<>();

        injectThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    pcap.loop(new PacketHandler()
                    {
                        @Override
                        public void nextPacket(io.pkts.packet.Packet packet)
                                throws IOException
                        {
                            if (closing)
                            {
                                // TODO There's no way to interrupt this loop, we're
                                // going to have to implement our own looper.
                                return;
                            }

                            byte[] buff = packet.getPacket(Protocol.UDP).getPayload().getArray();
                            RawPacket next = new RawPacket(buff, 0, buff.length);

                            long ssrc = RTPPacketPredicate.INSTANCE.test(next)
                                    ? next.getSSRCAsLong()
                                    : -1;

                            if (!emitterMap.containsKey(ssrc))
                            {
                                PacketEmitter pem = new PacketEmitter();
                                pem.start();
                                emitterMap.put(ssrc, pem);
                            }

                            PacketEmitter pem = emitterMap.get(ssrc);
                            try
                            {
                                pem.queue.put(next);
                            }
                            catch (InterruptedException e)
                            {
                            }
                        }
                    });
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });

        injectThread.start();
    }

    public void setConnector(StreamConnector connector)
    {
        stream.setConnector(connector);
    }

    public void setTarget(MediaStreamTarget target)
    {
        stream.setTarget(target);
    }

    public void updateMediaPacket(MediaPacketExtension mediaPacket)
    {
        if (ssrcs == null || ssrcs.length == 0)
        {
            String str = String.valueOf(stream.getLocalSourceID());
            mediaPacket.addSource(
                    stream.getFormat().getMediaType().toString(),
                    str,
                    MediaDirection.SENDRECV.toString());
        }
        else
        {
            for (int i = 0; i < ssrcs.length; i++)
            {
                mediaPacket.addSource(getFormat().getMediaType().toString(),
                        String.valueOf(ssrcs[i]),
                        MediaDirection.SENDRECV.toString());
            }
        }
    }

    public MediaFormat getFormat()
    {
        return format;
    }
}
