// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.junction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.wpilib.math.geometry.Translation2d;

/*
 * Tests for the early return when a put would store a value equal to the stored one: the table
 * must end up exactly as if the put had stored it.
 */
public class LogTableUnchangedPutTest {
  /** Puts first, then same (expecting no change), then different (expecting a change). */
  private static void check(
      Consumer<LogTable> first, Consumer<LogTable> same, Consumer<LogTable> different) {
    LogTable table = new LogTable(0);
    table.trackChanges();
    first.accept(table);
    table.takeChanges();
    LogTable.LogValue before = table.get("K");

    same.accept(table);
    assertEquals(Set.of(), table.takeChanges().getAll(false).keySet());
    assertEquals(before, table.get("K"));

    different.accept(table);
    assertEquals(Set.of("/K"), table.takeChanges().getAll(false).keySet());
  }

  @Test
  public void TestEveryTypeSkipsOnlyEqualValues() {
    check(t -> t.put("K", true), t -> t.put("K", true), t -> t.put("K", false));
    check(t -> t.put("K", 3L), t -> t.put("K", 3L), t -> t.put("K", 4L));
    check(t -> t.put("K", 3), t -> t.put("K", 3), t -> t.put("K", 4));
    check(t -> t.put("K", 1.5f), t -> t.put("K", 1.5f), t -> t.put("K", 2.5f));
    check(t -> t.put("K", 1.5f, "m"), t -> t.put("K", 1.5f, "m"), t -> t.put("K", 1.5f, "s"));
    check(t -> t.put("K", 1.5), t -> t.put("K", 1.5), t -> t.put("K", 2.5));
    check(t -> t.put("K", 1.5, "m"), t -> t.put("K", 1.5, "m"), t -> t.put("K", 1.5, "s"));
    check(t -> t.put("K", "a"), t -> t.put("K", new String("a")), t -> t.put("K", "b"));
    check(
        t -> t.put("K", new byte[] {1, 2}),
        t -> t.put("K", new byte[] {1, 2}),
        t -> t.put("K", new byte[] {1, 3}));
    check(
        t -> t.put("K", new boolean[] {true}),
        t -> t.put("K", new boolean[] {true}),
        t -> t.put("K", new boolean[] {false}));
    check(
        t -> t.put("K", new long[] {1}),
        t -> t.put("K", new long[] {1}),
        t -> t.put("K", new long[] {1, 2}));
    check(
        t -> t.put("K", new float[] {1}),
        t -> t.put("K", new float[] {1}),
        t -> t.put("K", new float[] {2}));
    check(
        t -> t.put("K", new double[] {1, 2}),
        t -> t.put("K", new double[] {1, 2}),
        t -> t.put("K", new double[] {}));
    check(
        t -> t.put("K", new String[] {"a"}),
        t -> t.put("K", new String[] {"a"}),
        t -> t.put("K", new String[] {"b"}));
  }

  @Test
  public void TestDoublesCompareAsDoubleEquals() {
    // NaN equals NaN and 0.0 differs from -0.0, as LogValue.equals (Double.equals) has it.
    check(t -> t.put("K", Double.NaN), t -> t.put("K", Double.NaN), t -> t.put("K", 0.0));
    check(t -> t.put("K", 0.0), t -> t.put("K", 0.0), t -> t.put("K", -0.0));
  }

  @Test
  public void TestStructsCompareEncodedBytes() {
    check(
        t -> t.put("K", new Translation2d(1, 2)),
        t -> t.put("K", new Translation2d(1, 2)),
        t -> t.put("K", new Translation2d(1, 3)));
    check(
        t -> t.put("K", new Translation2d[] {new Translation2d(1, 2)}),
        t -> t.put("K", new Translation2d[] {new Translation2d(1, 2)}),
        t -> t.put("K", new Translation2d[] {}));
  }

  @Test
  public void TestStructSchemaIsWrittenOnceAndRoundTrips() {
    LogTable table = new LogTable(0);
    table.put("A", new Translation2d(1, 2));
    table.put("B", new Translation2d[] {new Translation2d(3, 4), new Translation2d(5, 6)});
    assertEquals("struct:Translation2d", table.get("A").customTypeStr);
    assertEquals("struct:Translation2d[]", table.get("B").customTypeStr);
    assertEquals(new Translation2d(1, 2), table.get("A", Translation2d.ZERO));
    assertArrayEquals(
        new Translation2d[] {new Translation2d(3, 4), new Translation2d(5, 6)},
        table.get("B", new Translation2d[0]));
    assertEquals(1, countKeysStartingWith(table, "/.schema/struct:Translation2d"));
  }

  @Test
  public void TestSubtablesAreReused() {
    LogTable table = new LogTable(0);
    assertSame(table.getSubtable("A"), table.getSubtable("A"));
    table.getSubtable("A").getSubtable("B").put("C", 1.0);
    assertEquals(1.0, table.get("A/B/C", 0.0));
  }

  private static int countKeysStartingWith(LogTable table, String prefix) {
    int n = 0;
    for (String key : table.getAll(false).keySet()) {
      if (key.startsWith(prefix)) {
        n++;
      }
    }
    return n;
  }
}
