// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package convert

import (
	"encoding/json"
	"fmt"
	"math"
	"strconv"
	"strings"

	"github.com/gopcua/opcua/ua"
)

// WritableTypes lists the data types accepted by the write command.
var WritableTypes = []ua.TypeID{
	ua.TypeIDBoolean, ua.TypeIDSByte, ua.TypeIDByte, ua.TypeIDInt16, ua.TypeIDUint16,
	ua.TypeIDInt32, ua.TypeIDUint32, ua.TypeIDInt64, ua.TypeIDUint64, ua.TypeIDFloat,
	ua.TypeIDDouble, ua.TypeIDString, ua.TypeIDDateTime,
}

// WritableType parses a write data type name ("Double", ...).
func WritableType(name string) (ua.TypeID, bool) {
	t, ok := BuiltinTypeFromName(name)
	if !ok {
		return 0, false
	}
	for _, w := range WritableTypes {
		if w == t {
			return t, true
		}
	}
	return 0, false
}

// writableSubtypes maps standard (ns=0) subtypes of built-in types to the
// built-in type used to encode them.
var writableSubtypes = map[uint32]ua.TypeID{
	26:    ua.TypeIDDouble, // Number
	27:    ua.TypeIDInt64,  // Integer
	28:    ua.TypeIDUint64, // UInteger
	29:    ua.TypeIDInt32,  // Enumeration
	288:   ua.TypeIDUint32, // IntegerId
	289:   ua.TypeIDUint32, // Counter
	290:   ua.TypeIDDouble, // Duration
	291:   ua.TypeIDString, // NumericRange
	294:   ua.TypeIDDateTime,
	295:   ua.TypeIDString, // LocaleId
	12878: ua.TypeIDString, // NormalizedString
	12879: ua.TypeIDString, // DecimalString
	12880: ua.TypeIDString, // DurationString
	12881: ua.TypeIDString, // TimeString
	12882: ua.TypeIDString, // DateString
	11737: ua.TypeIDUint64, // BitFieldMaskDataType
}

// WritableTypeOfDataType maps the DataType attribute of a node to the
// built-in type used for writing.
func WritableTypeOfDataType(n *ua.NodeID) (ua.TypeID, bool) {
	if n == nil || n.Namespace() != 0 || n.Type() > ua.NodeIDTypeNumeric {
		return 0, false
	}
	i := n.IntID()
	if i >= 1 && i <= 13 {
		return ua.TypeID(i), true
	}
	t, ok := writableSubtypes[i]
	return t, ok
}

// WriteValue converts a JSON value (decoded with json.Decoder.UseNumber) to a
// Go value of the given built-in type. JSON arrays give typed slices.
func WriteValue(v any, t ua.TypeID) (any, error) {
	if arr, ok := v.([]any); ok {
		return writeArray(arr, t)
	}
	return writeScalar(v, t)
}

func writeArray(arr []any, t ua.TypeID) (any, error) {
	conv := make([]any, len(arr))
	for i, e := range arr {
		c, err := writeScalar(e, t)
		if err != nil {
			return nil, fmt.Errorf("element %d: %v", i, err)
		}
		conv[i] = c
	}
	switch t {
	case ua.TypeIDBoolean:
		return typed[bool](conv), nil
	case ua.TypeIDSByte:
		return typed[int8](conv), nil
	case ua.TypeIDByte:
		return typed[uint8](conv), nil
	case ua.TypeIDInt16:
		return typed[int16](conv), nil
	case ua.TypeIDUint16:
		return typed[uint16](conv), nil
	case ua.TypeIDInt32:
		return typed[int32](conv), nil
	case ua.TypeIDUint32:
		return typed[uint32](conv), nil
	case ua.TypeIDInt64:
		return typed[int64](conv), nil
	case ua.TypeIDUint64:
		return typed[uint64](conv), nil
	case ua.TypeIDFloat:
		return typed[float32](conv), nil
	case ua.TypeIDDouble:
		return typed[float64](conv), nil
	case ua.TypeIDString:
		return typed[string](conv), nil
	}
	return nil, fmt.Errorf("arrays of %s are not supported", BuiltinTypeName(t))
}

func typed[T any](in []any) []T {
	out := make([]T, len(in))
	for i, v := range in {
		out[i] = v.(T)
	}
	return out
}

func writeScalar(v any, t ua.TypeID) (any, error) {
	if v == nil {
		return nil, fmt.Errorf("value is null")
	}
	name := BuiltinTypeName(t)
	switch t {
	case ua.TypeIDBoolean:
		switch x := v.(type) {
		case bool:
			return x, nil
		case json.Number:
			f, err := x.Float64()
			if err != nil {
				return nil, err
			}
			return f != 0, nil
		case string:
			b, err := strconv.ParseBool(strings.TrimSpace(x))
			if err != nil {
				return nil, fmt.Errorf("not a Boolean: %q", x)
			}
			return b, nil
		}
	case ua.TypeIDSByte, ua.TypeIDInt16, ua.TypeIDInt32, ua.TypeIDInt64:
		bits := map[ua.TypeID]int{ua.TypeIDSByte: 8, ua.TypeIDInt16: 16, ua.TypeIDInt32: 32, ua.TypeIDInt64: 64}[t]
		s, err := numberText(v)
		if err != nil {
			return nil, err
		}
		i, err := strconv.ParseInt(s, 10, bits)
		if err != nil {
			// accept integral numbers in other notations, e.g. 21.0 or 1e3
			f, ferr := strconv.ParseFloat(s, 64)
			if ferr != nil || f != math.Trunc(f) {
				return nil, fmt.Errorf("not an integer: %s", s)
			}
			lim := math.Ldexp(1, bits-1)
			if f < -lim || f >= lim {
				return nil, fmt.Errorf("%s out of range for %s", s, name)
			}
			i = int64(f)
		}
		switch t {
		case ua.TypeIDSByte:
			return int8(i), nil
		case ua.TypeIDInt16:
			return int16(i), nil
		case ua.TypeIDInt32:
			return int32(i), nil
		}
		return i, nil
	case ua.TypeIDByte, ua.TypeIDUint16, ua.TypeIDUint32, ua.TypeIDUint64:
		bits := map[ua.TypeID]int{ua.TypeIDByte: 8, ua.TypeIDUint16: 16, ua.TypeIDUint32: 32, ua.TypeIDUint64: 64}[t]
		s, err := numberText(v)
		if err != nil {
			return nil, err
		}
		u, err := strconv.ParseUint(s, 10, bits)
		if err != nil {
			f, ferr := strconv.ParseFloat(s, 64)
			if ferr != nil || f != math.Trunc(f) || f < 0 || f >= math.Ldexp(1, bits) {
				return nil, fmt.Errorf("%s is not a valid %s", s, name)
			}
			u = uint64(f)
		}
		switch t {
		case ua.TypeIDByte:
			return uint8(u), nil
		case ua.TypeIDUint16:
			return uint16(u), nil
		case ua.TypeIDUint32:
			return uint32(u), nil
		}
		return u, nil
	case ua.TypeIDFloat, ua.TypeIDDouble:
		s, err := numberText(v)
		if err != nil {
			return nil, err
		}
		f, err := strconv.ParseFloat(s, 64)
		if err != nil {
			return nil, fmt.Errorf("not a number: %s", s)
		}
		if t == ua.TypeIDFloat {
			if math.Abs(f) > math.MaxFloat32 {
				return nil, fmt.Errorf("%s out of range for Float", s)
			}
			return float32(f), nil
		}
		return f, nil
	case ua.TypeIDString:
		switch x := v.(type) {
		case string:
			return x, nil
		case json.Number:
			return x.String(), nil
		case bool:
			return strconv.FormatBool(x), nil
		}
	case ua.TypeIDDateTime:
		if s, ok := v.(string); ok {
			return ParseTime(s)
		}
	default:
		return nil, fmt.Errorf("data type %s cannot be written", name)
	}
	return nil, fmt.Errorf("value %v cannot be converted to %s", v, name)
}

func numberText(v any) (string, error) {
	switch x := v.(type) {
	case json.Number:
		return x.String(), nil
	case string:
		return strings.TrimSpace(x), nil
	case bool:
		if x {
			return "1", nil
		}
		return "0", nil
	case float64:
		return strconv.FormatFloat(x, 'f', -1, 64), nil
	case int:
		return strconv.Itoa(x), nil
	}
	return "", fmt.Errorf("not a number: %v", v)
}
