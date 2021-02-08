/**
 * Copyright (C) 2017 WhatsApp Inc. All rights reserved.
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.groups;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.groups.ratchet.FastRatchetSenderChainKey;
import org.whispersystems.libsignal.groups.state.FastRatchetSenderKeyRecord;
import org.whispersystems.libsignal.groups.state.FastRatchetSenderKeyState;
import org.whispersystems.libsignal.groups.state.FastRatchetSenderKeyStore;
import org.whispersystems.libsignal.protocol.FastRatchetSenderKeyDistributionMessage;
import org.whispersystems.libsignal.util.FastRatchetUtil;
import org.whispersystems.libsignal.util.KeyHelper;

/**
 * FastRatchetGroupSessionBuilder is responsible for setting up group SenderKey encrypted sessions.
 *
 * Once a session has been established, {@link org.whispersystems.libsignal.groups.FastRatchetGroupCipher}
 * can be used to encrypt/decrypt messages in that session.
 * <p>
 * The built sessions are unidirectional: they can be used either for sending or for receiving,
 * but not both.
 *
 * Sessions are constructed per (groupId + senderId + deviceId) tuple.  Remote logical users
 * are identified by their senderId, and each logical recipientId can have multiple physical
 * devices.
 */
public class FastRatchetGroupSessionBuilder {

  private final FastRatchetSenderKeyStore senderKeyStore;

  public FastRatchetGroupSessionBuilder(FastRatchetSenderKeyStore senderKeyStore) {
    this.senderKeyStore = senderKeyStore;
  }

  /**
   * Construct a group session for receiving messages from senderKeyName.
   *
   * @param senderKeyName The (groupId, senderId, deviceId) tuple associated with the FastRatchetSenderKeyDistributionMessage.
   * @param senderKeyDistributionMessage A received FastRatchetSenderKeyDistributionMessage.
   */
  public void process(SenderKeyName senderKeyName, FastRatchetSenderKeyDistributionMessage senderKeyDistributionMessage) {
    synchronized (FastRatchetGroupCipher.LOCK) {
      FastRatchetSenderKeyRecord senderKeyRecord = senderKeyStore.loadFastRatchetSenderKey(senderKeyName);
      senderKeyRecord.addSenderKeyState(senderKeyDistributionMessage.getId(),
                                        senderKeyDistributionMessage.getIteration(),
                                        senderKeyDistributionMessage.getChainKeys(),
                                        senderKeyDistributionMessage.getSignatureKey());
      senderKeyStore.storeFastRatchetSenderKey(senderKeyName, senderKeyRecord);
    }
  }

  /**
   * Construct a group session for sending messages.
   *
   * @param senderKeyName The (groupId, senderId, deviceId) tuple.  In this case, 'senderId' should be the caller.
   * @param scale Value from which the number of dimensions for the chain key ratchet is derived.
   *              Valid values are from [0..5]. (Dimensions is 2^scale.)
   * @return A SenderKeyDistributionMessage that is individually distributed to each member of the group.
   */
  public FastRatchetSenderKeyDistributionMessage create(SenderKeyName senderKeyName, int scale) {
    if (scale < 0 || scale > 5) {
      throw new IllegalArgumentException("scale must be between 0 and 5");
    }

    synchronized (FastRatchetGroupCipher.LOCK) {
      try {
        FastRatchetSenderKeyRecord senderKeyRecord = senderKeyStore.loadFastRatchetSenderKey(senderKeyName);

        boolean createState = false;
        if (senderKeyRecord.isEmpty()) {
          createState = true;
        } else {
          FastRatchetSenderKeyState state = senderKeyRecord.getSenderKeyState();
          if (FastRatchetUtil.scaleToDimensions(scale) != state.getSenderChainKey().getSeeds().length) {
            createState = true;
          }
        }

        if (createState) {
          FastRatchetSenderChainKey initialChainKey = new FastRatchetSenderChainKey(KeyHelper.generateSenderKey(), scale);
          senderKeyRecord.setSenderKeyState(KeyHelper.generateSenderKeyId(),
                                            0,
                                            initialChainKey.getSeeds(),
                                            KeyHelper.generateSenderSigningKey());
          senderKeyStore.storeFastRatchetSenderKey(senderKeyName, senderKeyRecord);
        }

        FastRatchetSenderKeyState state = senderKeyRecord.getSenderKeyState();

        return new FastRatchetSenderKeyDistributionMessage(state.getKeyId(),
                                                           state.getSenderChainKey().getIteration(),
                                                           state.getSenderChainKey().getSeeds(),
                                                           state.getSigningKeyPublic());

      } catch (InvalidKeyIdException | InvalidKeyException e) {
        throw new AssertionError(e);
      }
    }
  }
}
