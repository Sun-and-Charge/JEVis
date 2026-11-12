// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package convert

import (
	"encoding/base64"
	"math"
	"reflect"
	"strings"
	"time"

	"github.com/gopcua/opcua/ua"
)

// builtinNames are the names of the OPC UA built-in types, indexed by type id.
var builtinNames = [...]string{
	"Null", "Boolean", "SByte", "Byte", "Int16", "UInt16", "Int32", "UInt32", "Int64", "UInt64",
	"Float", "Double", "String", "DateTime", "Guid", "ByteString", "XmlElement", "NodeId",
	"ExpandedNodeId", "StatusCode", "QualifiedName", "LocalizedText", "ExtensionObject",
	"DataValue", "Variant", "DiagnosticInfo",
}

// BuiltinTypeName returns the name of a built-in type id, e.g. "Double".
func BuiltinTypeName(t ua.TypeID) string {
	if int(t) < len(builtinNames) {
		return builtinNames[t]
	}
	return ""
}

// BuiltinTypeFromName returns the built-in type id of a name (case-insensitive).
func BuiltinTypeFromName(s string) (ua.TypeID, bool) {
	for i, n := range builtinNames {
		if i > 0 && strings.EqualFold(n, s) {
			return ua.TypeID(i), true
		}
	}
	return 0, false
}

// VariantType returns the built-in type name of a variant, or "" for an empty one.
func VariantType(v *ua.Variant) string {
	if v == nil || v.Type() == ua.TypeIDNull {
		return ""
	}
	return BuiltinTypeName(v.Type())
}

// VariantValue converts a variant to a JSON compatible value.
func VariantValue(v *ua.Variant) any {
	if v == nil {
		return nil
	}
	return GoValue(v.Value())
}

// GoValue converts a value decoded by gopcua to a JSON compatible value:
// numbers, strings, booleans, nil and slices of these.
func GoValue(v any) any {
	switch x := v.(type) {
	case nil:
		return nil
	case bool, string, int8, uint8, int16, uint16, int32, uint32, int64, uint64:
		return x
	case float32:
		return finite(float64(x))
	case float64:
		return finite(x)
	case time.Time:
		return FormatTimePtr(x)
	case *ua.GUID:
		if x == nil {
			return nil
		}
		return x.String()
	case []byte:
		return base64.StdEncoding.EncodeToString(x)
	case ua.ByteArray:
		// an array of Byte (not a ByteString): JSON array of numbers
		out := make([]any, len(x))
		for i, b := range x {
			out[i] = b
		}
		return out
	case ua.XMLElement:
		return string(x)
	case *ua.NodeID:
		if x == nil {
			return nil
		}
		return x.String()
	case *ua.ExpandedNodeID:
		if x == nil {
			return nil
		}
		return ExpandedNodeIDString(x)
	case ua.StatusCode:
		return uint32(x)
	case *ua.QualifiedName:
		if x == nil {
			return nil
		}
		return QualifiedNameString(x)
	case *ua.LocalizedText:
		if x == nil {
			return nil
		}
		return x.Text
	case *ua.DataValue:
		if x == nil {
			return nil
		}
		return VariantValue(x.Value)
	case *ua.Variant:
		return VariantValue(x)
	case *ua.ExtensionObject:
		// structures are not converted; their content is not needed by JEVis
		return nil
	case *ua.DiagnosticInfo:
		return nil
	}
	rv := reflect.ValueOf(v)
	if rv.Kind() == reflect.Slice || rv.Kind() == reflect.Array {
		out := make([]any, rv.Len())
		for i := range out {
			out[i] = GoValue(rv.Index(i).Interface())
		}
		return out
	}
	return nil
}

// finite returns nil for NaN and infinities, which JSON cannot represent.
func finite(f float64) any {
	if math.IsNaN(f) || math.IsInf(f, 0) {
		return nil
	}
	return f
}
