package edu.uci.ics.asterix.runtime.operators.file;

import java.util.Map;

import edu.uci.ics.asterix.common.parse.ITupleForwardPolicy;
import edu.uci.ics.hyracks.api.comm.IFrame;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.comm.VSizeFrame;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;

public class RateControlledTupleForwardPolicy implements ITupleForwardPolicy {

    private FrameTupleAppender appender;
    private IFrame frame;
    private IFrameWriter writer;
    private long interTupleInterval;
    private boolean delayConfigured;

    public static final String INTER_TUPLE_INTERVAL = "tuple-interval";

    public void configure(Map<String, String> configuration) {
        String propValue = configuration.get(INTER_TUPLE_INTERVAL);
        if (propValue != null) {
            interTupleInterval = Long.parseLong(propValue);
        }
        delayConfigured = interTupleInterval != 0;
    }

    public void initialize(IHyracksTaskContext ctx, IFrameWriter writer) throws HyracksDataException {
        this.appender = new FrameTupleAppender();
        this.frame = new VSizeFrame(ctx);
        this.writer = writer;
        appender.reset(frame, true);
    }

    public void addTuple(ArrayTupleBuilder tb) throws HyracksDataException {
        if (delayConfigured) {
            try {
                Thread.sleep(interTupleInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        boolean success = appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize());
        if (!success) {
            FrameUtils.flushFrame(frame.getBuffer(), writer);
            appender.reset(frame, true);
            success = appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize());
            if (!success) {
                throw new IllegalStateException();
            }
        }
    }

    public void close() throws HyracksDataException {
        if (appender.getTupleCount() > 0) {
            FrameUtils.flushFrame(frame.getBuffer(), writer);
        }

    }

    @Override
    public TupleForwardPolicyType getType() {
        return TupleForwardPolicyType.RATE_CONTROLLED;
    }
}