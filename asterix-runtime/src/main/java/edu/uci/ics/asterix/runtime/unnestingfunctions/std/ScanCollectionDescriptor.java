package edu.uci.ics.asterix.runtime.unnestingfunctions.std;

import java.io.DataOutput;
import java.io.IOException;

import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AOrderedListSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AUnorderedListSerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ANull;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.EnumDeserializer;
import edu.uci.ics.asterix.om.util.NonTaggedFormatUtil;
import edu.uci.ics.asterix.runtime.unnestingfunctions.base.AbstractUnnestingFunctionDynamicDescriptor;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IUnnestingFunction;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IUnnestingFunctionFactory;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class ScanCollectionDescriptor extends AbstractUnnestingFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    private final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS,
            "scan-collection", 1, true);

    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

    @Override
    public IUnnestingFunctionFactory createUnnestingFunctionFactory(final IEvaluatorFactory[] args) {
        return new ScanCollectionUnnestingFunctionFactory(args[0]);
    }

    private static class ScanCollectionUnnestingFunctionFactory implements IUnnestingFunctionFactory {

        private static final long serialVersionUID = 1L;

        private IEvaluatorFactory listEvalFactory;
        private final static byte SER_ORDEREDLIST_TYPE_TAG = ATypeTag.ORDEREDLIST.serialize();
        private final static byte SER_UNORDEREDLIST_TYPE_TAG = ATypeTag.UNORDEREDLIST.serialize();
        private final static byte SER_NULL_TYPE_TAG = ATypeTag.NULL.serialize();
        private ATypeTag itemTag;
        private byte serItemTypeTag;
        private boolean selfDescList = false;

        public ScanCollectionUnnestingFunctionFactory(IEvaluatorFactory arg) {
            this.listEvalFactory = arg;
        }

        @Override
        public IUnnestingFunction createUnnestingFunction(IDataOutputProvider provider) throws AlgebricksException {

            final DataOutput out = provider.getDataOutput();

            return new IUnnestingFunction() {

                private ArrayBackedValueStorage inputVal = new ArrayBackedValueStorage();
                private IEvaluator argEval = listEvalFactory.createEvaluator(inputVal);
                @SuppressWarnings("unchecked")
                private ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
                        .getSerializerDeserializer(BuiltinType.ANULL);
                private int numItems;
                private int pos;
                private int itemOffset;
                private int itemLength;
                private byte serListTag;

                @Override
                public void init(IFrameTupleReference tuple) throws AlgebricksException {
                    try {
                        inputVal.reset();
                        argEval.evaluate(tuple);
                        byte[] serList = inputVal.getBytes();

                        if (serList[0] == SER_NULL_TYPE_TAG) {
                            nullSerde.serialize(ANull.NULL, out);
                            return;
                        }

                        if (serList[0] != SER_ORDEREDLIST_TYPE_TAG && serList[0] != SER_UNORDEREDLIST_TYPE_TAG) {
                            throw new AlgebricksException("Scan collection is not defined for values of type"
                                    + EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(serList[0]));
                        }

                        serListTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(inputVal.getBytes()[0])
                                .serialize();
                        if (serListTag == SER_ORDEREDLIST_TYPE_TAG)
                            numItems = AOrderedListSerializerDeserializer.getNumberOfItems(inputVal.getBytes());
                        else
                            numItems = AUnorderedListSerializerDeserializer.getNumberOfItems(inputVal.getBytes());

                        itemTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(serList[1]);
                        if (itemTag == ATypeTag.ANY)
                            selfDescList = true;
                        else
                            serItemTypeTag = serList[1];

                        pos = 0;
                    } catch (IOException e) {
                        throw new AlgebricksException(e);
                    }
                }

                @Override
                public boolean step() throws AlgebricksException {

                    try {
                        if (pos < numItems) {
                            byte[] serList = inputVal.getBytes();

                            try {
                                if (serListTag == SER_ORDEREDLIST_TYPE_TAG) {
                                    itemOffset = AOrderedListSerializerDeserializer.getItemOffset(serList, pos);
                                } else {
                                    itemOffset = AUnorderedListSerializerDeserializer.getItemOffset(serList, pos);
                                }
                                if (selfDescList)
                                    itemTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(serList[itemOffset]);
                                itemLength = NonTaggedFormatUtil.getFieldValueLength(serList, itemOffset, itemTag,
                                        selfDescList);
                                if (!selfDescList)
                                    out.writeByte(serItemTypeTag);
                                out.write(serList, itemOffset, itemLength + (!selfDescList ? 0 : 1));
                            } catch (AsterixException e) {
                                throw new AlgebricksException(e);
                            }
                            ++pos;
                            return true;
                        } else
                            return false;

                    } catch (IOException e) {
                        throw new AlgebricksException(e);
                    }
                }

            };
        }

    }
}