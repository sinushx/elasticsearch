/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation.blockhash;

import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.LongHash;
import org.elasticsearch.compute.data.IntVector;
import org.elasticsearch.compute.data.LongArrayVector;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.LongVector;
import org.elasticsearch.compute.data.Page;

final class LongBlockHash extends BlockHash {
    private final int channel;
    private final LongHash longHash;

    LongBlockHash(int channel, BigArrays bigArrays) {
        this.channel = channel;
        this.longHash = new LongHash(1, bigArrays);
    }

    @Override
    public LongBlock add(Page page) {
        LongBlock block = page.getBlock(channel);
        int positionCount = block.getPositionCount();
        LongVector vector = block.asVector();
        if (vector != null) {
            long[] groups = new long[positionCount];
            for (int i = 0; i < positionCount; i++) {
                groups[i] = BlockHash.hashOrdToGroup(longHash.add(block.getLong(i)));
            }
            return new LongArrayVector(groups, positionCount).asBlock();
        }
        LongBlock.Builder builder = LongBlock.newBlockBuilder(positionCount);
        for (int i = 0; i < positionCount; i++) {
            if (block.isNull(i)) {
                builder.appendNull();
            } else {
                builder.appendLong(hashOrdToGroup(longHash.add(block.getLong(block.getFirstValueIndex(i)))));
            }
        }
        return builder.build();
    }

    @Override
    public LongBlock[] getKeys() {
        final int size = Math.toIntExact(longHash.size());
        final long[] keys = new long[size];
        for (int i = 0; i < size; i++) {
            keys[i] = longHash.get(i);
        }

        // TODO call something like takeKeyOwnership to claim the keys array directly
        return new LongBlock[] { new LongArrayVector(keys, keys.length).asBlock() };
    }

    @Override
    public IntVector nonEmpty() {
        return IntVector.range(0, Math.toIntExact(longHash.size()));
    }

    @Override
    public void close() {
        longHash.close();
    }

    @Override
    public String toString() {
        return "LongBlockHash{channel=" + channel + ", entries=" + longHash.size() + '}';
    }
}
