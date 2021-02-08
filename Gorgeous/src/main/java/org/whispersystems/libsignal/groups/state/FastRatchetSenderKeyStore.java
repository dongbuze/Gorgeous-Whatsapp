/**
 * Copyright (C) 2017 WhatsApp Inc. All rights reserved.
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.groups.state;

import org.whispersystems.libsignal.groups.SenderKeyName;

public interface FastRatchetSenderKeyStore {

  /**
   * Commit to storage the {@link org.whispersystems.libsignal.groups.state.FastRatchetSenderKeyRecord}
   * for a given (groupId + senderId + deviceId) tuple.
   *
   * @param senderKeyName the (groupId + senderId + deviceId) tuple.
   * @param record the current FastRatchetSenderKeyRecord for the specified senderKeyName.
   */
  public void storeFastRatchetSenderKey(SenderKeyName senderKeyName, FastRatchetSenderKeyRecord record);

  /**
   * Returns a copy of the {@link org.whispersystems.libsignal.groups.state.FastRatchetSenderKeyRecord}
   * corresponding to the (groupId + senderId + deviceId) tuple, or a new FastRatchetSenderKeyRecord if
   * one does not currently exist.
   * <p>
   * It is important that implementations return a copy of the current durable information.  The
   * returned FastRatchetSenderKeyRecord may be modified, but those changes should not have an
   * effect on the durable session state (what is returned by subsequent calls to this method)
   * without the store method being called here first.
   *
   * @param senderKeyName The (groupId + senderId + deviceId) tuple.
   * @return a copy of the FastRatchetSenderKeyRecord corresponding to the (groupId + senderId + deviceId
   *         tuple, or a new SenderKeyRecord if one does not currently exist.
   */

  public FastRatchetSenderKeyRecord loadFastRatchetSenderKey(SenderKeyName senderKeyName);
}
