/**
 * Copyright (C) 2017 WhatsApp Inc. All rights reserved.
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.groups.state;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.groups.ratchet.FastRatchetSenderChainKey;
import org.whispersystems.libsignal.util.FastRatchetUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import org.whispersystems.libsignal.state.StorageProtos.SenderKeyStateStructure;
import org.whispersystems.libsignal.state.StorageProtos.FastRatchetSenderKeyStateStructure;

/**
 * Represents the state of an individual SenderKey ratchet.
 */
public class FastRatchetSenderKeyState {

  private FastRatchetSenderKeyStateStructure senderKeyStateStructure;

  public FastRatchetSenderKeyState(int id, int iteration, byte[][] chainKeys, ECPublicKey signatureKey) {
    this(id, iteration, chainKeys, signatureKey, Optional.<ECPrivateKey>absent());
  }

  public FastRatchetSenderKeyState(int id, int iteration, byte[][] chainKeys, ECKeyPair signatureKey) {
    this(id, iteration, chainKeys, signatureKey.getPublicKey(), Optional.of(signatureKey.getPrivateKey()));
  }

  private FastRatchetSenderKeyState(int id, int iteration, byte[][] chainKeys,
                                    ECPublicKey signatureKeyPublic,
                                    Optional<ECPrivateKey> signatureKeyPrivate)
  {
    SenderKeyStateStructure.SenderSigningKey.Builder signingKeyStructure =
        SenderKeyStateStructure.SenderSigningKey.newBuilder()
                                                .setPublic(ByteString.copyFrom(signatureKeyPublic.serialize()));

    if (signatureKeyPrivate.isPresent()) {
      signingKeyStructure.setPrivate(ByteString.copyFrom(signatureKeyPrivate.get().serialize()));
    }

    FastRatchetSenderKeyStateStructure.Builder builder =
        FastRatchetSenderKeyStateStructure.newBuilder()
                                          .setSenderKeyId(id)
                                          .setSenderSigningKey(signingKeyStructure);

    int[] chainIterations = FastRatchetUtil.decomposeChainIterations(iteration, chainKeys.length);
    for (int i = 0; i < chainKeys.length; i++) {
      SenderKeyStateStructure.SenderChainKey senderChainKeyStructure =
          SenderKeyStateStructure.SenderChainKey.newBuilder()
                                                .setIteration(chainIterations[i])
                                                .setSeed(ByteString.copyFrom(chainKeys[i]))
                                                .build();
      builder.addSenderChainKeys(senderChainKeyStructure);
    }

    this.senderKeyStateStructure = builder.build();
  }

  public FastRatchetSenderKeyState(FastRatchetSenderKeyStateStructure senderKeyStateStructure) {
    this.senderKeyStateStructure = senderKeyStateStructure;
  }

  public int getKeyId() {
    return senderKeyStateStructure.getSenderKeyId();
  }

  public FastRatchetSenderChainKey getSenderChainKey() {
    return new FastRatchetSenderChainKey(senderKeyStateStructure.getSenderChainKeysList());
  }

  public void setSenderChainKey(FastRatchetSenderChainKey chainKey) {
    byte[][] seeds          = chainKey.getSeeds();
    int[]    seedIterations = FastRatchetUtil.decomposeChainIterations(chainKey.getIteration(), seeds.length);

    FastRatchetSenderKeyStateStructure.Builder builder = senderKeyStateStructure.toBuilder()
                                                                                .clearSenderChainKeys();

    for (int i = 0; i < seeds.length; i++) {
      SenderKeyStateStructure.SenderChainKey senderChainKeyStructure =
          SenderKeyStateStructure.SenderChainKey.newBuilder()
                                                .setIteration(seedIterations[i])
                                                .setSeed(ByteString.copyFrom(seeds[i]))
                                                .build();
      builder.addSenderChainKeys(senderChainKeyStructure);
    }

    this.senderKeyStateStructure = builder.build();
  }

  public ECPublicKey getSigningKeyPublic() throws InvalidKeyException {
    return Curve.decodePoint(senderKeyStateStructure.getSenderSigningKey()
                                                    .getPublic()
                                                    .toByteArray(), 0);
  }

  public ECPrivateKey getSigningKeyPrivate() {
    return Curve.decodePrivatePoint(senderKeyStateStructure.getSenderSigningKey()
                                                           .getPrivate().toByteArray());
  }

  public FastRatchetSenderKeyStateStructure getStructure() {
    return senderKeyStateStructure;
  }
}
