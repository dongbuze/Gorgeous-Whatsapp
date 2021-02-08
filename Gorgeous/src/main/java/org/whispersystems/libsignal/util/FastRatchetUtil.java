/**
 * Copyright (C) 2017 WhatsApp Inc. All rights reserved.
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.util;

/**
 * Helper class for various conversion operations needed by the fast ratchet
 * group cipher.
 */
public class FastRatchetUtil {
  private FastRatchetUtil() {}

  /**
   * Convert the "scale" parameter value into the number of actual key chains
   * it should represent.
   */
  public static int scaleToDimensions(int scale) {
    return 1 << scale;
  }

  static int composeIterations(int[] components) {
    int dimensions = components.length;
    int bits = 32 / dimensions;
    int mask = (int)((1L << bits) - 1);
    int result = 0;

    for (int i = 0; i < dimensions; i++) {
      result += (components[dimensions - i - 1] & mask) << (bits * i);
    }
    return result;
  }

  /**
   * Compose individual chain key iterations into the corresponding message
   * iteration.
   *
   * @param chainIterations Individual chain key iterations
   * @return Message iteration
   */
  public static int composeChainIterations(int[] chainIterations) {
    int[] result = new int[chainIterations.length];
    for (int i = 0; i < chainIterations.length - 1; i++) {
      result[i] = chainIterations[i] - 1;
    }
    result[chainIterations.length - 1] = chainIterations[chainIterations.length - 1];

    return composeIterations(result);
  }

  static int[] decomposeIteration(int iteration, int count) {
    int dimensions = count;
    int bits = 32 / dimensions;
    int mask = (int)((1L << bits) - 1);
    int[] result = new int[count];

    for (int i = 0; i < dimensions; i++) {
      result[dimensions - i - 1] = (iteration >> bits * i) & mask;
    }

    return result;
  }

  /**
   * Decompose a message iteration into the individual chain key iterations.
   *
   * @param iteration The iteration to decompose
   * @param chainCount The number of chains in the cipher
   * @return Individual chain key iterations
   */
  public static int[] decomposeChainIterations(int iteration, int chainCount) {
    int[] result = decomposeIteration(iteration, chainCount);
    for (int i = 0; i < result.length - 1; i++) {
      result[i]++;
    }
    return result;
  }
}
