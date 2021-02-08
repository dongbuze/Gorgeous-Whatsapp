/**
 * Copyright (C) 2017 WhatsApp Inc. All rights reserved.
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.groups.ratchet;

import org.whispersystems.libsignal.state.StorageProtos;
import org.whispersystems.libsignal.util.FastRatchetUtil;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Each SenderKey is a "chain" of keys, each derived from the previous.
 *
 * At any given point in time, the state of a SenderKey can be represented
 * as the current chain key value, along with its iteration count.  From there,
 * subsequent iterations can be derived, as well as individual message keys from
 * each chain key.
 */
public class FastRatchetSenderChainKey {

  private static final byte MESSAGE_KEY_SEED = (byte) 0x01;
  private static final byte CHAIN_KEY_SEED   = (byte) 0x02;

  private final int      iteration;
  private final byte[][] chainKeys;

  public FastRatchetSenderChainKey(byte[] baseChainKey, int scale) {
    if (scale < 0 || scale > 5) {
      throw new IllegalArgumentException("scale must be between 0 and 5");
    }

    int count = FastRatchetUtil.scaleToDimensions(scale);
    this.iteration = 0;

    this.chainKeys = new byte[count][];
    this.chainKeys[0] = baseChainKey;

    for (int i = 1; i < count; i++) {
      this.chainKeys[i] = new byte[0];
    }
  }

  public FastRatchetSenderChainKey(int iteration, byte[][] chainKeys) {
    if (chainKeys.length == 0 || 32 % chainKeys.length != 0) {
      throw new IllegalArgumentException("Invalid number of chain keys: " + chainKeys.length);
    }

    validateChainKeyParameters(iteration, chainKeys);

    this.iteration = iteration;
    this.chainKeys = chainKeys;
  }

  public FastRatchetSenderChainKey(List<StorageProtos.SenderKeyStateStructure.SenderChainKey> senderChainKeysList) {
    if (senderChainKeysList.isEmpty() || 32 % senderChainKeysList.size() != 0) {
      throw new IllegalArgumentException("Invalid number of chain keys: " + senderChainKeysList.size());
    }

    int   chainCount          = senderChainKeysList.size();
    int[] iterationComponents = new int[chainCount];
    this.chainKeys = new byte[chainCount][];

    for (int i = 0; i < chainCount; i++) {
      iterationComponents[i] = senderChainKeysList.get(i).getIteration();
      this.chainKeys[i] = senderChainKeysList.get(i).getSeed().toByteArray();
    }

    this.iteration = FastRatchetUtil.composeChainIterations(iterationComponents);

    validateChainKeyParameters(this.iteration, this.chainKeys);
  }

  private static void validateChainKeyParameters(int iteration, byte[][] chainKeys) {
    if (iteration == 0 && chainKeys.length > 1 && chainKeys[1].length == 0) {
      for (int i = 2; i < chainKeys.length; i++) {
        if (chainKeys[i].length > 0) {
          throw new IllegalArgumentException("Invalid chain key values for starting iteration");
        }
      }
    } else {
      for (int i = 0; i < chainKeys.length; i++) {
        if (chainKeys[i].length == 0) {
          throw new IllegalArgumentException("Invalid chain key values");
        }
      }
    }
  }

  public int getIteration() {
    return iteration;
  }

  public SenderMessageKey getSenderMessageKey() {
    byte[][] chainKeysToUse = getDerivedSeeds();
    return new SenderMessageKey(getIteration(), getDerivative(MESSAGE_KEY_SEED, chainKeysToUse[chainKeysToUse.length - 1]));
  }

  public FastRatchetSenderChainKey getNext() {
    return getNext(1);
  }

  public FastRatchetSenderChainKey getNext(int count) {
    if (count <= 0) {
      throw new IllegalArgumentException("count must be a positive number: " + count);
    }

    int   nextIteration        = iteration + count;
    int[] currentDecomposition = FastRatchetUtil.decomposeChainIterations(iteration, chainKeys.length);
    int[] nextDecomposition    = FastRatchetUtil.decomposeChainIterations(nextIteration, chainKeys.length);

    byte[][] chainKeysToUse = getDerivedSeeds();

    ratchetChainKeys(currentDecomposition, nextDecomposition, chainKeysToUse);

    return new FastRatchetSenderChainKey(nextIteration, chainKeysToUse);
  }

  private void ratchetChainKeys(int[] currentDecomposition, int[] nextDecomposition, byte[][] chains) {
    for (int i = 0; i < chains.length; i++) {
      while (nextDecomposition[i] > currentDecomposition[i]) {
        if (i < chains.length - 1 && nextDecomposition[i] - 1 == currentDecomposition[i]) {
          chains[i + 1] = getDerivative((byte) (CHAIN_KEY_SEED + i + 1), chains[i]);
          currentDecomposition[i + 1] = 0;
        }
        chains[i] = getDerivative((byte) (CHAIN_KEY_SEED + i), chains[i]);
        currentDecomposition[i]++;
      }
    }
  }

  public byte[][] getSeeds() {
    return chainKeys;
  }

  /**
   * This method is only exposed for testing.
   */
  public byte[][] getDerivedSeeds() {
    byte[][] seeds = new byte[chainKeys.length][];

    if (iteration == 0 && chainKeys.length > 1 && chainKeys[1].length == 0) {
      for (int i = 0; i < chainKeys.length - 1; i++) {
        byte[] currentKey = (i == 0) ? chainKeys[0] : seeds[i];
        seeds[i + 1] = getDerivative((byte) (CHAIN_KEY_SEED + i + 1), currentKey);
        seeds[i] = getDerivative((byte) (CHAIN_KEY_SEED + i), currentKey);
      }
    } else {
      for (int i = 0; i < chainKeys.length; i++) {
        seeds[i] = new byte[chainKeys[i].length];
        System.arraycopy(chainKeys[i], 0, seeds[i], 0, chainKeys[i].length);
      }
    }

    return seeds;
  }

  private byte[] getDerivative(byte seed, byte[] key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      mac.update(seed);
      return mac.doFinal();
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

}
