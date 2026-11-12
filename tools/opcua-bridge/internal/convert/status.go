// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package convert

import (
	"fmt"
	"strings"

	"github.com/gopcua/opcua/ua"
)

// gopcua generates Go style names (BadNodeIDUnknown); this restores the
// names of the OPC UA specification (BadNodeIdUnknown). It is the inverse of
// gopcua's cmd/service/goname replacements.
var specNames = strings.NewReplacer(
	"GUID", "Guid",
	"ID", "Id",
	"JSON", "Json",
	"QoS", "QualityOfService",
	"TCP", "Tcp",
	"UADP", "Uadp",
	"URI", "Uri",
	"URL", "Url",
	"XML", "Xml",
)

// StatusName returns the OPC UA name of a status code, e.g. "Good",
// "BadNodeIdUnknown". The info bits (lower 16 bits) are ignored; unknown codes
// are returned as hex.
func StatusName(c ua.StatusCode) string {
	base := c & 0xFFFF0000
	switch base {
	case ua.StatusGood:
		return "Good"
	case ua.StatusUncertain:
		return "Uncertain"
	case ua.StatusBad:
		return "Bad"
	}
	if d, ok := ua.StatusCodes[base]; ok {
		return specNames.Replace(strings.TrimPrefix(d.Name, "Status"))
	}
	return fmt.Sprintf("0x%08X", uint32(c))
}

// IsBad reports whether the severity of the status code is Bad.
func IsBad(c ua.StatusCode) bool { return uint32(c)&0x80000000 != 0 }

// IsUncertain reports whether the severity of the status code is Uncertain.
func IsUncertain(c ua.StatusCode) bool { return uint32(c)&0xC0000000 == 0x40000000 }
