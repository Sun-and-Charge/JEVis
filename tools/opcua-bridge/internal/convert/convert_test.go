// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package convert

import (
	"encoding/json"
	"math"
	"reflect"
	"testing"
	"time"

	"github.com/gopcua/opcua/ua"
)

func TestNodeIDRoundTrip(t *testing.T) {
	for _, s := range []string{
		"i=84", "ns=2;s=Device1.Temp", "ns=1;i=1001", "ns=3;g=5CE9DBCE-5D79-434C-9AC3-1CFBA9A6E92C", "ns=3;b=YWJj",
	} {
		n, err := ParseNodeID(s)
		if err != nil {
			t.Fatalf("%s: %v", s, err)
		}
		if got := NodeIDString(n); got != s {
			t.Errorf("round trip %s -> %s", s, got)
		}
	}
	for _, s := range []string{"", "ns=x;i=1", "ns=1;q=2"} {
		if _, err := ParseNodeID(s); err == nil {
			t.Errorf("%q: expected error", s)
		}
	}
}

func TestExpandedNodeID(t *testing.T) {
	e := ua.NewExpandedNodeID(ua.NewStringNodeID(2, "a"), "", 0)
	if got := ExpandedNodeIDString(e); got != "ns=2;s=a" {
		t.Error(got)
	}
	e = &ua.ExpandedNodeID{NodeID: ua.NewNumericNodeID(2, 5), NamespaceURI: "urn:x"}
	if got := ExpandedNodeIDString(e); got != "nsu=urn:x;i=5" {
		t.Error(got)
	}
	if !IsNullNodeID(ua.NewTwoByteNodeID(0)) || IsNullNodeID(ua.NewNumericNodeID(0, 58)) {
		t.Error("IsNullNodeID")
	}
}

func TestStatusName(t *testing.T) {
	cases := map[ua.StatusCode]string{
		0:                                 "Good",
		ua.StatusBadNodeIDUnknown:         "BadNodeIdUnknown",
		ua.StatusGoodNoData:               "GoodNoData",
		ua.StatusBadNoData:                "BadNoData",
		ua.StatusUncertainLastUsableValue: "UncertainLastUsableValue",
		ua.StatusBadCertificateURIInvalid: "BadCertificateUriInvalid",
		ua.StatusBadIdentityTokenInvalid:  "BadIdentityTokenInvalid",
		ua.StatusBadTCPServerTooBusy:      "BadTcpServerTooBusy",
		ua.StatusBadUserAccessDenied:      "BadUserAccessDenied",
		0x00000400:                        "Good", // info bits only (calculated)
		ua.StatusGoodNoData | 0x0400:      "GoodNoData",
		0x80000000:                        "Bad",
		0x8FFF0000:                        "0x8FFF0000",
	}
	for c, want := range cases {
		if got := StatusName(c); got != want {
			t.Errorf("0x%08X: %s, want %s", uint32(c), got, want)
		}
	}
	if !IsBad(ua.StatusBadNoData) || IsBad(ua.StatusGoodNoData) || !IsUncertain(ua.StatusUncertainLastUsableValue) {
		t.Error("severity")
	}
}

func TestTime(t *testing.T) {
	ts := time.Date(2026, 11, 12, 8, 15, 0, 120_456_000, time.UTC)
	if got := FormatTime(ts); got != "2026-11-12T08:15:00.120Z" {
		t.Error(got)
	}
	if FormatTimePtr(time.Time{}) != nil || FormatTime(time.Date(1601, 1, 1, 0, 0, 0, 0, time.UTC)) != "" {
		t.Error("zero time must be null")
	}
	cases := map[string]time.Time{
		"2026-11-01T00:00:00Z":          time.Date(2026, 11, 1, 0, 0, 0, 0, time.UTC),
		"2026-11-01T01:00:00+01:00":     time.Date(2026, 11, 1, 0, 0, 0, 0, time.UTC),
		"2026-11-01T01:00:00.500+0100":  time.Date(2026, 11, 1, 0, 0, 0, 500e6, time.UTC),
		"2026-11-01T00:00:00":           time.Date(2026, 11, 1, 0, 0, 0, 0, time.UTC),
		"2026-11-12T08:15:00.000Z":      time.Date(2026, 11, 12, 8, 15, 0, 0, time.UTC),
		"2026-11-12T09:15:00.123-01:00": time.Date(2026, 11, 12, 10, 15, 0, 123e6, time.UTC),
	}
	for s, want := range cases {
		got, err := ParseTime(s)
		if err != nil || !got.Equal(want) || got.Location() != time.UTC {
			t.Errorf("%s: %v %v", s, got, err)
		}
	}
	if _, err := ParseTime("yesterday"); err == nil {
		t.Error("expected error")
	}
}

func TestVariantValue(t *testing.T) {
	ts := time.Date(2026, 11, 12, 8, 15, 0, 0, time.UTC)
	cases := []struct {
		in       any
		want     any
		wantType string
	}{
		{21.5, 21.5, "Double"},
		{float32(1.5), 1.5, "Float"},
		{int32(-3), int32(-3), "Int32"},
		{uint64(1 << 63), uint64(1 << 63), "UInt64"},
		{true, true, "Boolean"},
		{"x", "x", "String"},
		{ts, "2026-11-12T08:15:00.000Z", "DateTime"},
		{[]byte{1, 2, 3}, "AQID", "ByteString"},
		{ua.NewStringNodeID(2, "a"), "ns=2;s=a", "NodeId"},
		{&ua.QualifiedName{NamespaceIndex: 2, Name: "Temp"}, "2:Temp", "QualifiedName"},
		{&ua.LocalizedText{Text: "Hallo", Locale: "de"}, "Hallo", "LocalizedText"},
		{[]float64{1, 2}, []any{1.0, 2.0}, "Double"},
		{[]string{"a"}, []any{"a"}, "String"},
		{math.NaN(), nil, "Double"},
		{ua.StatusBadNoData, uint32(ua.StatusBadNoData), "StatusCode"},
	}
	for _, c := range cases {
		v, err := ua.NewVariant(c.in)
		if err != nil {
			t.Fatalf("%v: %v", c.in, err)
		}
		if got := VariantValue(v); !reflect.DeepEqual(got, c.want) {
			t.Errorf("%T %v: got %#v want %#v", c.in, c.in, got, c.want)
		}
		if got := VariantType(v); got != c.wantType {
			t.Errorf("%T: type %s want %s", c.in, got, c.wantType)
		}
	}
	if VariantValue(nil) != nil || VariantType(nil) != "" {
		t.Error("nil variant")
	}
	// JSON encoding of all converted values must work
	for _, c := range cases {
		v, _ := ua.NewVariant(c.in)
		if _, err := json.Marshal(VariantValue(v)); err != nil {
			t.Errorf("%T: %v", c.in, err)
		}
	}
}

func TestDataTypeName(t *testing.T) {
	cases := map[string]string{
		"i=11":         "Double",
		"i=6":          "Int32",
		"i=294":        "UtcTime",
		"ns=2;i=3001":  "ns=2;i=3001",
		"ns=0;s=Thing": "s=Thing",
	}
	for in, want := range cases {
		n, _ := ParseNodeID(in)
		if got := DataTypeName(n); got != want {
			t.Errorf("%s: %s want %s", in, got, want)
		}
	}
}

func TestNodeClass(t *testing.T) {
	c, ok := NodeClassFromName("variable")
	if !ok || c != ua.NodeClassVariable || NodeClassName(c) != "Variable" {
		t.Error("Variable")
	}
	if _, ok := NodeClassFromName("Thing"); ok {
		t.Error("Thing")
	}
}

func num(s string) json.Number { return json.Number(s) }

func TestWriteValue(t *testing.T) {
	ts := time.Date(2026, 11, 12, 8, 15, 0, 0, time.UTC)
	ok := []struct {
		in   any
		typ  string
		want any
	}{
		{true, "Boolean", true},
		{num("0"), "Boolean", false},
		{"true", "Boolean", true},
		{num("-5"), "SByte", int8(-5)},
		{num("200"), "Byte", uint8(200)},
		{num("-30000"), "Int16", int16(-30000)},
		{num("60000"), "UInt16", uint16(60000)},
		{num("21"), "Int32", int32(21)},
		{num("21.0"), "Int32", int32(21)},
		{"42", "Int32", int32(42)},
		{num("4000000000"), "UInt32", uint32(4000000000)},
		{num("-9223372036854775808"), "Int64", int64(math.MinInt64)},
		{num("18446744073709551615"), "UInt64", uint64(math.MaxUint64)},
		{num("1.5"), "Float", float32(1.5)},
		{num("21.5"), "Double", 21.5},
		{"21.5", "Double", 21.5},
		{"text", "String", "text"},
		{num("12"), "String", "12"},
		{"2026-11-12T09:15:00+01:00", "DateTime", ts},
		{[]any{num("1"), num("2")}, "Double", []float64{1, 2}},
		{[]any{num("1"), num("2")}, "Int32", []int32{1, 2}},
		{[]any{true}, "Boolean", []bool{true}},
	}
	for _, c := range ok {
		typ, found := WritableType(c.typ)
		if !found {
			t.Fatalf("type %s", c.typ)
		}
		got, err := WriteValue(c.in, typ)
		if err != nil {
			t.Errorf("%v as %s: %v", c.in, c.typ, err)
			continue
		}
		if !reflect.DeepEqual(got, c.want) {
			t.Errorf("%v as %s: %#v want %#v", c.in, c.typ, got, c.want)
		}
		if _, err := ua.NewVariant(got); err != nil {
			t.Errorf("%v as %s: variant: %v", c.in, c.typ, err)
		}
	}
	bad := []struct {
		in  any
		typ string
	}{
		{num("300"), "Byte"},
		{num("-1"), "UInt32"},
		{num("1.5"), "Int32"},
		{num("40000"), "Int16"},
		{"abc", "Double"},
		{"maybe", "Boolean"},
		{nil, "Double"},
		{num("1"), "DateTime"},
		{num("1e40"), "Float"},
	}
	for _, c := range bad {
		typ, _ := WritableType(c.typ)
		if _, err := WriteValue(c.in, typ); err == nil {
			t.Errorf("%v as %s: expected error", c.in, c.typ)
		}
	}
	if _, ok := WritableType("Guid"); ok {
		t.Error("Guid must not be writable")
	}
}

func TestWritableTypeOfDataType(t *testing.T) {
	cases := map[string]ua.TypeID{
		"i=11":  ua.TypeIDDouble,
		"i=1":   ua.TypeIDBoolean,
		"i=12":  ua.TypeIDString,
		"i=290": ua.TypeIDDouble,
		"i=294": ua.TypeIDDateTime,
		"i=29":  ua.TypeIDInt32,
	}
	for in, want := range cases {
		n, _ := ParseNodeID(in)
		got, ok := WritableTypeOfDataType(n)
		if !ok || got != want {
			t.Errorf("%s: %v %v", in, got, ok)
		}
	}
	for _, in := range []string{"i=15", "ns=2;i=11", "i=22"} {
		n, _ := ParseNodeID(in)
		if _, ok := WritableTypeOfDataType(n); ok {
			t.Errorf("%s: expected not writable", in)
		}
	}
}
