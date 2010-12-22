/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.dyn.util;

public enum Wrapper {
    BOOLEAN(Boolean.class, boolean.class, 'Z', (Boolean)false, Format.unsigned(1)),
    // These must be in the order defined for widening primitive conversions in JLS 5.1.2
    BYTE(Byte.class, byte.class, 'B', (Byte)(byte)0, Format.signed(8)),
    SHORT(Short.class, short.class, 'S', (Short)(short)0, Format.signed(16)),
    CHAR(Character.class, char.class, 'C', (Character)(char)0, Format.unsigned(16)),
    INT(Integer.class, int.class, 'I', (Integer)(int)0, Format.signed(32)),
    LONG(Long.class, long.class, 'J', (Long)(long)0, Format.signed(64)),
    FLOAT(Float.class, float.class, 'F', (Float)(float)0, Format.floating(32)),
    DOUBLE(Double.class, double.class, 'D', (Double)(double)0, Format.floating(64)),
    //NULL(Null.class, null.class, 'N', null, Format.other(1)),
    OBJECT(Object.class, Object.class, 'L', null, Format.other(1)),
    // VOID must be the last type, since it is "assignable" from any other type:
    VOID(Void.class, void.class, 'V', null, Format.other(0)),
    ;

    private final Class<?> wrapperType;
    private final Class<?> primitiveType;
    private final char     basicTypeChar;
    private final Object   zero;
    private final int      format;
    private final String   simpleName;

    private Wrapper(Class<?> wtype, Class<?> ptype, char tchar, Object zero, int format) {
        this.wrapperType = wtype;
        this.primitiveType = ptype;
        this.basicTypeChar = tchar;
        this.zero = zero;
        this.format = format;
        this.simpleName = wtype.getSimpleName();
    }

    private static abstract class Format {
        static final int SLOT_SHIFT = 0, SIZE_SHIFT = 2, KIND_SHIFT = 12;
        static final int
                SIGNED   = (-1) << KIND_SHIFT,
                UNSIGNED = 0    << KIND_SHIFT,
                FLOATING = 1    << KIND_SHIFT;
        static final int
                SLOT_MASK = ((1<<(SIZE_SHIFT-SLOT_SHIFT))-1),
                SIZE_MASK = ((1<<(KIND_SHIFT-SIZE_SHIFT))-1);
        static int format(int kind, int size, int slots) {
            assert(((kind >> KIND_SHIFT) << KIND_SHIFT) == kind);
            assert((size & (size-1)) == 0); // power of two
            assert((kind == SIGNED)   ? (size > 0) :
                   (kind == UNSIGNED) ? (size > 0) :
                   (kind == FLOATING) ? (size == 32 || size == 64)  :
                   false);
            assert((slots == 2) ? (size == 64) :
                   (slots == 1) ? (size <= 32) :
                   false);
            return kind | (size << SIZE_SHIFT) | (slots << SLOT_SHIFT);
        }
        static final int
                INT      = SIGNED   | (32 << SIZE_SHIFT) | (1 << SLOT_SHIFT),
                SHORT    = SIGNED   | (16 << SIZE_SHIFT) | (1 << SLOT_SHIFT),
                BOOLEAN  = UNSIGNED | (1  << SIZE_SHIFT) | (1 << SLOT_SHIFT),
                CHAR     = UNSIGNED | (16 << SIZE_SHIFT) | (1 << SLOT_SHIFT),
                FLOAT    = FLOATING | (32 << SIZE_SHIFT) | (1 << SLOT_SHIFT),
                VOID     = UNSIGNED | (0  << SIZE_SHIFT) | (0 << SLOT_SHIFT),
                NUM_MASK = (-1) << SIZE_SHIFT;
        static int signed(int size)   { return format(SIGNED,   size, (size > 32 ? 2 : 1)); }
        static int unsigned(int size) { return format(UNSIGNED, size, (size > 32 ? 2 : 1)); }
        static int floating(int size) { return format(FLOATING, size, (size > 32 ? 2 : 1)); }
        static int other(int slots)   { return slots << SLOT_SHIFT; }
    }

    /// format queries:

    /** How many bits are in the wrapped value?  Returns 0 for OBJECT or VOID. */
    public int     bitWidth()      { return (format >> Format.SIZE_SHIFT) & Format.SIZE_MASK; }
    /** How many JVM stack slots occupied by the wrapped value?  Returns 0 for VOID. */
    public int     stackSlots()    { return (format >> Format.SLOT_SHIFT) & Format.SLOT_MASK; }
    /** Does the wrapped value occupy a single JVM stack slot? */
    public boolean isSingleWord()  { return (format & (1 << Format.SLOT_SHIFT)) != 0; }
    /** Does the wrapped value occupy two JVM stack slots? */
    public boolean isDoubleWord()  { return (format & (2 << Format.SLOT_SHIFT)) != 0; }
    /** Is the wrapped type numeric (not void or object)? */
    public boolean isNumeric()     { return (format & Format.NUM_MASK) != 0; }
    /** Is the wrapped type a primitive other than float, double, or void? */
    public boolean isIntegral()    { return isNumeric() && format < Format.FLOAT; }
    /** Is the wrapped type one of int, boolean, byte, char, or short? */
    public boolean isSubwordOrInt() { return isIntegral() && isSingleWord(); }
    /* Is the wrapped value a signed integral type (one of byte, short, int, or long)? */
    public boolean isSigned()      { return format < Format.VOID; }
    /* Is the wrapped value an unsigned integral type (one of boolean or char)? */
    public boolean isUnsigned()    { return format >= Format.BOOLEAN && format < Format.FLOAT; }
    /** Is the wrapped type either float or double? */
    public boolean isFloating()    { return format >= Format.FLOAT; }

    /** Does the JVM verifier allow a variable of this wrapper's
     *  primitive type to be assigned from a value of the given wrapper's primitive type?
     *  Cases:
     *  <ul>
     *  <li>unboxing followed by widening primitive conversion
     *  <li>any type converted to {@code void}
     *  <li>boxing conversion followed by widening reference conversion to {@code Object}
     *  <li>conversion of {@code boolean} to any type
     *  </ul>
     */
    public boolean isConvertibleFrom(Wrapper source) {
        if (this == source)  return true;
        if (this.compareTo(source) < 0) {
            // At best, this is a narrowing conversion.
            return false;
        }
        if ((this.format ^ source.format) == (Format.SHORT ^ Format.CHAR)) {
            assert (this == SHORT && source == CHAR) || (this == CHAR && source == SHORT);
            return false;
        }
        return true;
    }

    /** Produce a zero value for the given wrapper type.
     *  This will be a numeric zero for a number or character,
     *  false for a boolean, and null for a reference or void.
     *  The common thread is that this is what is contained
     *  in a default-initialized variable of the given primitive
     *  type.  (For void, it is what a reflective method returns
     *  instead of no value at all.)
     */
    public Object zero() { return zero; }

    /** Produce a zero value for the given wrapper type T.
     *  The optional argument must a type compatible with this wrapper.
     *  Equivalent to {@code this.cast(this.zero(), type)}.
     */
    public <T> T zero(Class<T> type) { return convert(zero, type); }

//    /** Produce a wrapper for the given wrapper or primitive type. */
//    public static Wrapper valueOf(Class<?> type) {
//        if (isPrimitiveType(type))
//            return forPrimitiveType(type);
//        else
//            return forWrapperType(type);
//    }

    /** Return the wrapper that wraps values of the given type.
     *  The type may be {@code Object}, meaning the {@code OBJECT} wrapper.
     *  Otherwise, the type must be a primitive.
     *  @throws IllegalArgumentException for unexpected types
     */
    public static Wrapper forPrimitiveType(Class<?> type) {
        Wrapper w = findPrimitiveType(type);
        if (w != null)  return w;
        if (type.isPrimitive())
            throw new InternalError(); // redo hash function
        throw newIllegalArgumentException("not primitive: "+type);
    }

    static Wrapper findPrimitiveType(Class<?> type) {
        Wrapper w = FROM_PRIM[hashPrim(type)];
        if (w != null && w.primitiveType == type) {
            return w;
        }
        return null;
    }

    /** Return the wrapper that wraps values into the given wrapper type.
     *  If it is {@code Object} or an interface, return {@code OBJECT}.
     *  Otherwise, it must be a wrapper type.
     *  The type must not be a primitive type.
     *  @throws IllegalArgumentException for unexpected types
     */
    public static Wrapper forWrapperType(Class<?> type) {
        Wrapper w = findWrapperType(type);
        if (w != null)  return w;
        for (Wrapper x : values())
            if (x.wrapperType == type)
                throw new InternalError(); // redo hash function
        throw newIllegalArgumentException("not wrapper: "+type);
    }

    static Wrapper findWrapperType(Class<?> type) {
        Wrapper w = FROM_WRAP[hashWrap(type)];
        if (w != null && w.wrapperType == type) {
            return w;
        }
        if (type.isInterface())
            return OBJECT;
        return null;
    }

    /** Return the wrapper that corresponds to the given bytecode
     *  signature character.  Return {@code OBJECT} for the character 'L'.
     *  @throws IllegalArgumentException for any non-signature character or {@code '['}.
     */
    public static Wrapper forBasicType(char type) {
        Wrapper w = FROM_CHAR[hashChar(type)];
        if (w != null && w.basicTypeChar == type) {
            return w;
        }
        for (Wrapper x : values())
            if (w.basicTypeChar == type)
                throw new InternalError(); // redo hash function
        throw newIllegalArgumentException("not basic type char: "+type);
    }

    /** Return the wrapper for the given type, if it is
     *  a primitive type, else return {@code OBJECT}.
     */
    public static Wrapper forBasicType(Class<?> type) {
        if (type.isPrimitive())
            return forPrimitiveType(type);
        return OBJECT;  // any reference, including wrappers or arrays
    }

    // Note on perfect hashes:
    //   for signature chars c, do (c + (c >> 1)) % 16
    //   for primitive type names n, do (n[0] + n[2]) % 16
    // The type name hash works for both primitive and wrapper names.
    // You can add "java/lang/Object" to the primitive names.
    // But you add the wrapper name Object, use (n[2] + (3*n[1])) % 16.
    private static final Wrapper[] FROM_PRIM = new Wrapper[16];
    private static final Wrapper[] FROM_WRAP = new Wrapper[16];
    private static final Wrapper[] FROM_CHAR = new Wrapper[16];
    private static int hashPrim(Class<?> x) {
        String xn = x.getName();
        if (xn.length() < 3)  return 0;
        return (xn.charAt(0) + xn.charAt(2)) % 16;
    }
    private static int hashWrap(Class<?> x) {
        String xn = x.getName();
        final int offset = 10; assert(offset == "java.lang.".length());
        if (xn.length() < offset+3)  return 0;
        return (3*xn.charAt(offset+1) + xn.charAt(offset+2)) % 16;
    }
    private static int hashChar(char x) {
        return (x + (x >> 1)) % 16;
    }
    static {
        for (Wrapper w : values()) {
            int pi = hashPrim(w.primitiveType);
            int wi = hashWrap(w.wrapperType);
            int ci = hashChar(w.basicTypeChar);
            assert(FROM_PRIM[pi] == null);
            assert(FROM_WRAP[wi] == null);
            assert(FROM_CHAR[ci] == null);
            FROM_PRIM[pi] = w;
            FROM_WRAP[wi] = w;
            FROM_CHAR[ci] = w;
        }
        //assert(jdk.sun.dyn.util.WrapperTest.test(false));
    }

    /** What is the primitive type wrapped by this wrapper? */
    public Class<?> primitiveType() { return primitiveType; }

    /** What is the wrapper type for this wrapper? */
    public Class<?> wrapperType() { return wrapperType; }

    /** What is the wrapper type for this wrapper?
     * Otherwise, the example type must be the wrapper type,
     * or the corresponding primitive type.
     * (For {@code OBJECT}, the example type can be any non-primitive,
     * and is normalized to {@code Object.class}.)
     * The resulting class type has the same type parameter.
     */
    public <T> Class<T> wrapperType(Class<T> exampleType) {
        if (exampleType == wrapperType) {
            return exampleType;
        } else if (exampleType == primitiveType ||
                   wrapperType == Object.class ||
                   exampleType.isInterface()) {
            return forceType(wrapperType, exampleType);
        }
        throw newClassCastException(exampleType, primitiveType);
    }

    private static ClassCastException newClassCastException(Class<?> actual, Class<?> expected) {
        return new ClassCastException(actual + " is not compatible with " + expected);
    }

    /** If {@code type} is a primitive type, return the corresponding
     *  wrapper type, else return {@code type} unchanged.
     */
    public static <T> Class<T> asWrapperType(Class<T> type) {
        if (type.isPrimitive()) {
            return forPrimitiveType(type).wrapperType(type);
        }
        return type;
    }

    /** If {@code type} is a wrapper type, return the corresponding
     *  primitive type, else return {@code type} unchanged.
     */
    public static <T> Class<T> asPrimitiveType(Class<T> type) {
        Wrapper w = findWrapperType(type);
        if (w != null) {
            return forceType(w.primitiveType(), type);
        }
        return type;
    }

    /** Query:  Is the given type a wrapper, such as {@code Integer} or {@code Void}? */
    public static boolean isWrapperType(Class<?> type) {
        return findWrapperType(type) != null;
    }

    /** Query:  Is the given type a primitive, such as {@code int} or {@code void}? */
    public static boolean isPrimitiveType(Class<?> type) {
        return type.isPrimitive();
    }

    /** What is the bytecode signature character for this type?
     *  All non-primitives, including array types, report as 'L', the signature character for references.
     */
    public static char basicTypeChar(Class<?> type) {
        if (!type.isPrimitive())
            return 'L';
        else
            return forPrimitiveType(type).basicTypeChar();
    }

    /** What is the bytecode signature character for this wrapper's
     *  primitive type?
     */
    public char basicTypeChar() { return basicTypeChar; }

    /** What is the simple name of the wrapper type?
     */
    public String simpleName() { return simpleName; }

//    /** Wrap a value in the given type, which may be either a primitive or wrapper type.
//     *  Performs standard primitive conversions, including truncation and float conversions.
//     */
//    public static <T> T wrap(Object x, Class<T> type) {
//        return Wrapper.valueOf(type).cast(x, type);
//    }

    /** Cast a wrapped value to the given type, which may be either a primitive or wrapper type.
     *  The given target type must be this wrapper's primitive or wrapper type.
     *  If this wrapper is OBJECT, the target type may also be an interface, perform no runtime check.
     *  Performs standard primitive conversions, including truncation and float conversions.
     *  The given type must be compatible with this wrapper.  That is, it must either
     *  be the wrapper type (or a subtype, in the case of {@code OBJECT}) or else
     *  it must be the wrapper's primitive type.
     *  Primitive conversions are only performed if the given type is itself a primitive.
     *  @throws ClassCastException if the given type is not compatible with this wrapper
     */
    public <T> T cast(Object x, Class<T> type) {
        return convert(x, type, true);
    }

    /** Convert a wrapped value to the given type.
     *  The given target type must be this wrapper's primitive or wrapper type.
     *  This is equivalent to {@link #cast}, except that it refuses to perform
     *  narrowing primitive conversions.
     */
    public <T> T convert(Object x, Class<T> type) {
        return convert(x, type, false);
    }

    private <T> T convert(Object x, Class<T> type, boolean isCast) {
        if (this == OBJECT) {
            // If the target wrapper is OBJECT, just do a reference cast.
            // If the target type is an interface, perform no runtime check.
            // (This loophole is safe, and is allowed by the JVM verifier.)
            // If the target type is a primitive, change it to a wrapper.
            @SuppressWarnings("unchecked")
            T result = (T) x;  // unchecked warning is expected here
            return result;
        }
        Class<T> wtype = wrapperType(type);
        if (wtype.isInstance(x)) {
            @SuppressWarnings("unchecked")
            T result = (T) x;  // unchecked warning is expected here
            return result;
        }
        Class<?> sourceType = x.getClass();  // throw NPE if x is null
        if (!isCast) {
            Wrapper source = findWrapperType(sourceType);
            if (source == null || !this.isConvertibleFrom(source)) {
                throw newClassCastException(wtype, sourceType);
            }
        }
        @SuppressWarnings("unchecked")
        T result = (T) wrap(x);  // unchecked warning is expected here
        assert result.getClass() == wtype;
        return result;
    }

    /** Cast a reference type to another reference type.
     * If the target type is an interface, perform no runtime check.
     * (This loophole is safe, and is allowed by the JVM verifier.)
     * If the target type is a primitive, change it to a wrapper.
     */
    static <T> Class<T> forceType(Class<?> type, Class<T> exampleType) {
        boolean z = (type == exampleType ||
               type.isPrimitive() && forPrimitiveType(type) == findWrapperType(exampleType) ||
               exampleType.isPrimitive() && forPrimitiveType(exampleType) == findWrapperType(type) ||
               type == Object.class && !exampleType.isPrimitive());
        if (!z)
            System.out.println(type+" <= "+exampleType);
        assert(type == exampleType ||
               type.isPrimitive() && forPrimitiveType(type) == findWrapperType(exampleType) ||
               exampleType.isPrimitive() && forPrimitiveType(exampleType) == findWrapperType(type) ||
               type == Object.class && !exampleType.isPrimitive());
        @SuppressWarnings("unchecked")
        Class<T> result = (Class<T>) type;  // unchecked warning is expected here
        return result;
    }

    /** Wrap a value in this wrapper's type.
     * Performs standard primitive conversions, including truncation and float conversions.
     * Performs returns the unchanged reference for {@code OBJECT}.
     * Returns null for {@code VOID}.
     * Returns a zero value for a null input.
     * @throws ClassCastException if this wrapper is numeric and the operand
     *                            is not a number, character, boolean, or null
     */
    public Object wrap(Object x) {
        // do non-numeric wrappers first
        switch (basicTypeChar) {
            case 'L': return x;
            case 'V': return null;
        }
        Number xn = numberValue(x);
        switch (basicTypeChar) {
            case 'I': return Integer.valueOf(xn.intValue());
            case 'J': return Long.valueOf(xn.longValue());
            case 'F': return Float.valueOf(xn.floatValue());
            case 'D': return Double.valueOf(xn.doubleValue());
            case 'S': return Short.valueOf((short) xn.intValue());
            case 'B': return Byte.valueOf((byte) xn.intValue());
            case 'C': return Character.valueOf((char) xn.intValue());
            case 'Z': return Boolean.valueOf(boolValue(xn.longValue()));
        }
        throw new InternalError("bad wrapper");
    }

    /** Wrap a value (an int or smaller value) in this wrapper's type.
     * Performs standard primitive conversions, including truncation and float conversions.
     * Produces an {@code Integer} for {@code OBJECT}, although the exact type
     * of the operand is not known.
     * Returns null for {@code VOID}.
     */
    public Object wrap(int x) {
        if (basicTypeChar == 'L')  return (Integer)x;
        switch (basicTypeChar) {
            case 'L': throw newIllegalArgumentException("cannot wrap to object type");
            case 'V': return null;
            case 'I': return Integer.valueOf((int)x);
            case 'J': return Long.valueOf(x);
            case 'F': return Float.valueOf(x);
            case 'D': return Double.valueOf(x);
            case 'S': return Short.valueOf((short) x);
            case 'B': return Byte.valueOf((byte) x);
            case 'C': return Character.valueOf((char) x);
            case 'Z': return Boolean.valueOf(boolValue(x));
        }
        throw new InternalError("bad wrapper");
    }

    /** Wrap a value (a long or smaller value) in this wrapper's type.
     * Does not perform floating point conversion.
     * Produces a {@code Long} for {@code OBJECT}, although the exact type
     * of the operand is not known.
     * Returns null for {@code VOID}.
     */
    public Object wrapRaw(long x) {
        switch (basicTypeChar) {
            case 'F':  return Float.valueOf(Float.intBitsToFloat((int)x));
            case 'D':  return Double.valueOf(Double.longBitsToDouble(x));
            case 'L':  // same as 'J':
            case 'J':  return (Long) x;
        }
        // Other wrapping operations are just the same, given that the
        // operand is already promoted to an int.
        return wrap((int)x);
    }

    /** Produce bitwise value which encodes the given wrapped value.
     * Does not perform floating point conversion.
     * Returns zero for {@code VOID}.
     */
    public long unwrapRaw(Object x) {
        switch (basicTypeChar) {
            case 'F':  return Float.floatToRawIntBits((Float) x);
            case 'D':  return Double.doubleToRawLongBits((Double) x);

            case 'L': throw newIllegalArgumentException("cannot unwrap from sobject type");
            case 'V': return 0;
            case 'I': return (int)(Integer) x;
            case 'J': return (long)(Long) x;
            case 'S': return (short)(Short) x;
            case 'B': return (byte)(Byte) x;
            case 'C': return (char)(Character) x;
            case 'Z': return (boolean)(Boolean) x ? 1 : 0;
        }
        throw new InternalError("bad wrapper");
    }

    /** Report what primitive type holds this guy's raw value. */
    public Class<?> rawPrimitiveType() {
        return rawPrimitive().primitiveType();
    }

    /** Report, as a wrapper, what primitive type holds this guy's raw value.
     *  Returns self for INT, LONG, OBJECT; returns LONG for DOUBLE,
     *  else returns INT.
     */
    public Wrapper rawPrimitive() {
        switch (basicTypeChar) {
            case 'S': case 'B':
            case 'C': case 'Z':
            case 'V':
            case 'F':
                return INT;
            case 'D':
                return LONG;
        }
        return this;
    }

    private static Number numberValue(Object x) {
        if (x instanceof Number)     return (Number)x;
        if (x instanceof Character)  return (int)(Character)x;
        if (x instanceof Boolean)    return (Boolean)x ? 1 : 0;
        // Remaining allowed case of void:  Must be a null reference.
        return (Number)x;
    }

    private static boolean boolValue(long bits) {
        //bits &= 1;  // simple 31-bit zero extension
        return (bits != 0);
    }

    private static RuntimeException newIllegalArgumentException(String message, Object x) {
        return newIllegalArgumentException(message + x);
    }
    private static RuntimeException newIllegalArgumentException(String message) {
        return new IllegalArgumentException(message);
    }
}
