// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.junction.wpilog;

import java.io.IOException;
import java.util.Arrays;
import org.littletonrobotics.junction.LogDataReceiver;
import org.littletonrobotics.junction.LogReplaySource;
import org.littletonrobotics.junction.LogTable;
import org.littletonrobotics.junction.LogTable.LogValue;
import org.littletonrobotics.junction.LogTable.LoggableType;
import org.wpilib.datalog.DataLogIterator;
import org.wpilib.datalog.DataLogReader;
import org.wpilib.datalog.DataLogRecord;
import org.wpilib.driverstation.DriverStationErrors;

/** Replays log values from a WPILOG file. */
public class WPILOGReader implements LogReplaySource {
  private final String filename;
  private boolean isValid;

  private DataLogReader reader;
  private DataLogIterator iterator;

  private Long timestamp;

  /**
   * What a data record needs from its entry's start record, by entry id: worked out once per entry
   * instead of with four boxed map lookups and a new key string per record.
   */
  private static final class Entry {
    /** The table key: the entry name without its leading slash. */
    final String key;

    final boolean isTimestamp;

    /** An old run's replay outputs, never read back. */
    final boolean skip;

    final LoggableType type;
    final String customType;
    String unit;

    Entry(String name, LoggableType type, String customType, String unit) {
      this.key = name.substring(1);
      this.isTimestamp = name.equals(LogDataReceiver.timestampKey);
      this.skip = key.startsWith("ReplayOutputs");
      this.type = type;
      this.customType = customType;
      this.unit = unit;
    }
  }

  private Entry[] entries;

  private Entry entry(int id) {
    return id >= 0 && id < entries.length ? entries[id] : null;
  }

  /**
   * Creates a new WPILOGReader.
   *
   * @param filename The log filename to read.
   */
  public WPILOGReader(String filename) {
    this.filename = filename;
  }

  public void start() {
    // Open log file
    try {
      reader = new DataLogReader(filename);
    } catch (IOException e) {
      DriverStationErrors.reportError("[AdvantageKit] Failed to open replay log file.", true);
    }

    // Check validity
    if (!reader.isValid()) {
      DriverStationErrors.reportError(
          "[AdvantageKit] The replay log is not a valid WPILOG file.", false);
      isValid = false;
    } else if (!reader.getExtraHeader().equals(WPILOGConstants.extraHeader)) {
      DriverStationErrors.reportError(
          "[AdvantageKit] The replay log was not produced by AdvantageKit.", true);
      isValid = false;
    } else {
      isValid = true;
    }

    // Create iterator and reset
    iterator = reader.iterator();
    timestamp = null;
    entries = new Entry[256];
  }

  private String parseUnit(String metadata) {
    if (metadata == null || !metadata.contains("\"unit\":\"")) {
      return null;
    }
    int startIndex = metadata.indexOf("\"unit\":\"") + 8;
    int endIndex = metadata.indexOf("\"", startIndex);
    if (endIndex != -1) {
      return metadata.substring(startIndex, endIndex);
    }
    return null;
  }

  public boolean updateTable(LogTable table) {
    if (!isValid) {
      return false;
    }

    // Update timestamp (except the first cycle b/c it isn't known yet)
    if (timestamp != null) {
      table.setTimestamp(timestamp);
    }

    // Iterate over log
    boolean readError = false;
    while (iterator.hasNext()) {
      DataLogRecord record;
      try {
        record = iterator.next();
      } catch (Exception e) {
        readError = true;
        break;
      }

      if (record.isControl()) {
        if (record.isStart()) {
          var start = record.getStartData();
          String typeStr = start.type;
          var loggableType = LoggableType.fromWPILOGType(typeStr);
          String customType =
              (loggableType == LoggableType.Raw && !typeStr.equals("raw")) || typeStr.equals("json")
                  ? typeStr
                  : null;

          // Parse and store the unit from the start record
          String unit = parseUnit(start.metadata);

          // Check timestamp unit
          if (start.name.equals(LogDataReceiver.timestampKey)) {
            if (!"nanoseconds".equals(unit)) {
              throw new RuntimeException(
                  "[AdvantageKit] The replay log uses an incompatible timestamp unit. Logs must be recorded and replayed on the same version of AdvantageKit.");
            }
          }
          if (start.entry >= entries.length) {
            entries = Arrays.copyOf(entries, Math.max(start.entry + 1, entries.length * 2));
          }
          entries[start.entry] = new Entry(start.name, loggableType, customType, unit);
        } else if (record.isSetMetadata()) {
          // Handle metadata updates dynamically
          var metadata = record.getSetMetadataData();
          Entry known = entry(metadata.entry);
          if (known != null) {
            known.unit = parseUnit(metadata.metadata);
          }
        }
      } else {
        Entry entry = entry(record.getEntry());
        if (entry != null) {
          if (entry.isTimestamp) {
            boolean firstTimestamp = timestamp == null;
            timestamp = record.getInteger();
            if (firstTimestamp) {
              table.setTimestamp(timestamp);
            } else {
              break; // End of cycle
            }

          } else if (timestamp != null && !entry.skip) {
            String key = entry.key;
            String customType = entry.customType;
            String unit = entry.unit;

            switch (entry.type) {
              case Raw:
                table.put(key, new LogValue(record.getRaw(), customType));
                break;
              case Boolean:
                table.put(key, new LogValue(record.getBoolean(), customType));
                break;
              case Integer:
                table.put(key, new LogValue(record.getInteger(), customType));
                break;
              case Float:
                if (unit != null) {
                  table.put(key, new LogValue(record.getFloat(), customType, unit));
                } else {
                  table.put(key, new LogValue(record.getFloat(), customType));
                }
                break;
              case Double:
                if (unit != null) {
                  table.put(key, new LogValue(record.getDouble(), customType, unit));
                } else {
                  table.put(key, new LogValue(record.getDouble(), customType));
                }
                break;
              case String:
                table.put(key, new LogValue(record.getString(), customType));
                break;
              case BooleanArray:
                table.put(key, new LogValue(record.getBooleanArray(), customType));
                break;
              case IntegerArray:
                table.put(key, new LogValue(record.getIntegerArray(), customType));
                break;
              case FloatArray:
                table.put(key, new LogValue(record.getFloatArray(), customType));
                break;
              case DoubleArray:
                table.put(key, new LogValue(record.getDoubleArray(), customType));
                break;
              case StringArray:
                table.put(key, new LogValue(record.getStringArray(), customType));
                break;
            }
          }
        }
      }
    }

    // Continue if there is more data
    return iterator.hasNext() && !readError;
  }
}
