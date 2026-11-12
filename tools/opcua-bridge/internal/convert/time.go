// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package convert

import (
	"fmt"
	"strings"
	"time"
)

// TimeLayout is the timestamp format of all responses.
const TimeLayout = "2006-01-02T15:04:05.000Z"

// minTime is the OPC UA minimum DateTime (1601-01-01), i.e. "no timestamp".
var minTime = time.Date(1601, 1, 2, 0, 0, 0, 0, time.UTC)

// FormatTime formats a timestamp as ISO 8601 UTC with milliseconds; "" for
// a missing timestamp.
func FormatTime(t time.Time) string {
	if t.IsZero() || t.Before(minTime) {
		return ""
	}
	return t.UTC().Format(TimeLayout)
}

// FormatTimePtr is FormatTime with nil for a missing timestamp.
func FormatTimePtr(t time.Time) any {
	s := FormatTime(t)
	if s == "" {
		return nil
	}
	return s
}

var parseLayouts = []string{
	time.RFC3339Nano,
	"2006-01-02T15:04:05.999999999Z0700",
	"2006-01-02T15:04:05.999999999Z07",
	"2006-01-02T15:04Z07:00",
	"2006-01-02",
}

// ParseTime parses an ISO 8601 timestamp with any offset. A timestamp without
// offset is taken as UTC.
func ParseTime(s string) (time.Time, error) {
	s = strings.TrimSpace(s)
	for _, l := range parseLayouts {
		if t, err := time.Parse(l, s); err == nil {
			return t.UTC(), nil
		}
	}
	// no offset: UTC
	if t, err := time.ParseInLocation("2006-01-02T15:04:05.999999999", s, time.UTC); err == nil {
		return t, nil
	}
	return time.Time{}, fmt.Errorf("invalid timestamp %q (ISO 8601 expected)", s)
}
