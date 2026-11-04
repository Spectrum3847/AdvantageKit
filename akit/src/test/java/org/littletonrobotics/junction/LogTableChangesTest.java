// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.junction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/*
 * Tests for the changed-entries handoff from the main thread to the receiver thread.
 */
public class LogTableChangesTest {
  @Test
  public void TestEntriesBeforeTrackingCountAsChanged() {
    LogTable table = new LogTable(0);
    table.put("A", 1.0);
    table.trackChanges();
    assertEquals(Set.of("/A"), table.takeChanges().getAll(false).keySet());
  }

  @Test
  public void TestUnchangedValueIsNotSentAgain() {
    LogTable table = new LogTable(0);
    table.trackChanges();
    table.put("A", 1.0);
    table.put("B", new double[] {1, 2});
    table.takeChanges();

    table.put("A", 1.0);
    table.put("B", new double[] {1, 2});
    assertEquals(Set.of(), table.takeChanges().getAll(false).keySet());

    table.put("B", new double[] {1, 3});
    var changes = table.takeChanges();
    assertEquals(Set.of("/B"), changes.getAll(false).keySet());
    assertEquals(3.0, changes.get("B", new double[0])[1]);
  }

  @Test
  public void TestSubtableKeysAreAbsolute() {
    LogTable table = new LogTable(0);
    table.trackChanges();
    table.getSubtable("Swerve").getSubtable("Module0").put("Angle", 0.5);
    assertEquals(Set.of("/Swerve/Module0/Angle"), table.takeChanges().getAll(false).keySet());
    // The cached key is per subtable prefix, so the same short key elsewhere is separate
    table.getSubtable("Hood").put("Angle", 0.25);
    assertEquals(Set.of("/Hood/Angle"), table.takeChanges().getAll(false).keySet());
  }

  @Test
  public void TestChangesCarryTheTimestamp() {
    LogTable table = new LogTable(0);
    table.trackChanges();
    table.setTimestamp(123);
    table.put("A", true);
    assertEquals(123, table.takeChanges().getTimestamp());
  }

  @Test
  public void TestRestoredChangesLoseToNewerValues() {
    LogTable table = new LogTable(0);
    table.trackChanges();
    table.put("A", 1.0);
    table.put("B", 1.0);
    var undelivered = table.takeChanges();

    table.put("A", 2.0);
    table.restoreChanges(undelivered);
    var changes = table.takeChanges();
    assertEquals(2.0, changes.get("A", 0.0));
    assertEquals(1.0, changes.get("B", 0.0));
  }

  @Test
  public void TestReceiverTableMatchesSource() {
    LogTable source = new LogTable(0);
    source.trackChanges();
    LogTable receiver = new LogTable(0);
    for (int cycle = 0; cycle < 5; cycle++) {
      source.setTimestamp(cycle);
      source.put("Cycle", cycle);
      if (cycle % 2 == 0) {
        source.getSubtable("Even").put("Last", cycle);
      }
      receiver.applyChanges(source.takeChanges());
      assertEquals(source.getAll(false), receiver.getAll(false));
      assertEquals(cycle, receiver.getTimestamp());
    }
  }

  @Test
  public void TestRepeatAfterSeedingAddsNothing() {
    LogTable table = new LogTable(0);
    table.put("A", 1.0);
    table.trackChanges();
    table.put("A", 1.0);
    // Seeded with what was there; the repeated value adds nothing more
    assertEquals(Set.of("/A"), table.takeChanges().getAll(false).keySet());
  }
}
