/**
 * Copyright (C) 2017 WhatsApp Inc. All rights reserved.
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.ByteUtil;

import java.util.List;

public class FastRatchetSenderKeyDistributionMessage implements CiphertextMessage {

  private final int         id;
  private final int         iteration;
  private final byte[][]    chainKeys;
  private final ECPublicKey signatureKey;
  private final byte[]      serialized;

  public FastRatchetSenderKeyDistributionMessage(int id, int iteration, byte[][] chainKeys, ECPublicKey signatureKey) {
    byte[] version = {ByteUtil.intsToByteHighAndLow(CURRENT_VERSION, CURRENT_VERSION)};

    SignalProtos.FastRatchetSenderKeyDistributionMessage.Builder builder = SignalProtos.FastRatchetSenderKeyDistributionMessage.newBuilder()
            .setId(id)
            .setIteration(iteration)
            .setSigningKey(ByteString.copyFrom(signatureKey.serialize()));

    for (byte[] chainKey : chainKeys) {
      builder.addChainKeys(ByteString.copyFrom(chainKey));
    }

    byte[] protobuf = builder.build().toByteArray();

    this.id           = id;
    this.iteration    = iteration;
    this.chainKeys    = chainKeys;
    this.signatureKey = signatureKey;
    this.serialized   = ByteUtil.combine(version, protobuf);
  }

  public FastRatchetSenderKeyDistributionMessage(byte[] serialized) throws LegacyMessageException, InvalidMessageException {
    try {
      byte[][] messageParts = ByteUtil.split(serialized, 1, serialized.length - 1);
      byte     version      = messageParts[0][0];
      byte[]   message      = messageParts[1];

      if (ByteUtil.highBitsToInt(version) < CiphertextMessage.CURRENT_VERSION) {
        throw new LegacyMessageException("Legacy message: " + ByteUtil.highBitsToInt(version));
      }

      if (ByteUtil.highBitsToInt(version) > CURRENT_VERSION) {
        throw new InvalidMessageException("Unknown version: " + ByteUtil.highBitsToInt(version));
      }

      SignalProtos.FastRatchetSenderKeyDistributionMessage distributionMessage = SignalProtos.FastRatchetSenderKeyDistributionMessage.parseFrom(message);

      if (!distributionMessage.hasId()        ||
              !distributionMessage.hasIteration() ||
              distributionMessage.getChainKeysCount() <= 0 ||
              !distributionMessage.hasSigningKey())
      {
        throw new InvalidMessageException("Incomplete message.");
      }

      this.serialized   = serialized;
      this.id           = distributionMessage.getId();
      this.iteration    = distributionMessage.getIteration();
      this.signatureKey = Curve.decodePoint(distributionMessage.getSigningKey().toByteArray(), 0);

      List<ByteString> chainKeyList = distributionMessage.getChainKeysList();
      this.chainKeys = new byte[chainKeyList.size()][];
      for (int i = 0; i < this.chainKeys.length; i++) {
        this.chainKeys[i] = chainKeyList.get(i).toByteArray();
      }

    } catch (InvalidProtocolBufferException | InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  @Override
  public byte[] serialize() {
    return serialized;
  }

  @Override
  public int getType() {
    return FASTRATCHET_SENDERKEY_DISTRIBUTION_TYPE;
  }

  public int getIteration() {
    return iteration;
  }

  public byte[][] getChainKeys() {
    return chainKeys;
  }

  public ECPublicKey getSignatureKey() {
    return signatureKey;
  }

  public int getId() {
    return id;
  }
}
