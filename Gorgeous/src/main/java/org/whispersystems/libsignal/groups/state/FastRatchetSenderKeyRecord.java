/**
 * Copyright (C) 2017 WhatsApp Inc. All rights reserved.
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.groups.state;

import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.StorageProtos;

import java.io.IOException;
import java.util.LinkedList;

/**
 * A durable representation of a set of FastRatchetSenderKeyStates for a specific
 * SenderKeyName.
 */
public class FastRatchetSenderKeyRecord {

  private static final int MAX_STATES = 5;

  private LinkedList<FastRatchetSenderKeyState> senderKeyStates = new LinkedList<>();

  public FastRatchetSenderKeyRecord() {}

  public FastRatchetSenderKeyRecord(byte[] serialized) throws IOException {
    StorageProtos.FastRatchetSenderKeyRecordStructure senderKeyRecordStructure =
            StorageProtos.FastRatchetSenderKeyRecordStructure.parseFrom(serialized);

    for (StorageProtos.FastRatchetSenderKeyStateStructure structure : senderKeyRecordStructure.getSenderKeyStatesList()) {
      this.senderKeyStates.add(new FastRatchetSenderKeyState(structure));
    }
  }

  public boolean isEmpty() {
    return senderKeyStates.isEmpty();
  }

  public FastRatchetSenderKeyState getSenderKeyState() throws InvalidKeyIdException {
    if (!senderKeyStates.isEmpty()) {
      return senderKeyStates.get(0);
    } else {
      throw new InvalidKeyIdException("No key state in record!");
    }
  }

  public FastRatchetSenderKeyState getSenderKeyState(int keyId) throws InvalidKeyIdException {
    for (FastRatchetSenderKeyState state : senderKeyStates) {
      if (state.getKeyId() == keyId) {
        return state;
      }
    }

    throw new InvalidKeyIdException("No keys for: " + keyId);
  }

  public void addSenderKeyState(int id, int iteration, byte[][] chainKeys, ECPublicKey signatureKey) {
    senderKeyStates.addFirst(new FastRatchetSenderKeyState(id, iteration, chainKeys, signatureKey));

    if (senderKeyStates.size() > MAX_STATES) {
      senderKeyStates.removeLast();
    }
  }

  public void setSenderKeyState(int id, int iteration, byte[][] chainKeys, ECKeyPair signatureKey) {
    senderKeyStates.clear();
    senderKeyStates.add(new FastRatchetSenderKeyState(id, iteration, chainKeys, signatureKey));
  }

  public byte[] serialize() {
    StorageProtos.FastRatchetSenderKeyRecordStructure.Builder recordStructure =
            StorageProtos.FastRatchetSenderKeyRecordStructure.newBuilder();

    for (FastRatchetSenderKeyState senderKeyState : senderKeyStates) {
      recordStructure.addSenderKeyStates(senderKeyState.getStructure());
    }

    return recordStructure.build().toByteArray();
  }
}
