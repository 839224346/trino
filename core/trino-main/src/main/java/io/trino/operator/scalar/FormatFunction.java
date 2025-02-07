/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.operator.scalar;

import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;
import io.trino.annotation.UsedByGeneratedCode;
import io.trino.metadata.SqlScalarFunction;
import io.trino.spi.TrinoException;
import io.trino.spi.block.Block;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.function.BoundSignature;
import io.trino.spi.function.CatalogSchemaFunctionName;
import io.trino.spi.function.FunctionDependencies;
import io.trino.spi.function.FunctionDependencyDeclaration;
import io.trino.spi.function.FunctionDependencyDeclaration.FunctionDependencyDeclarationBuilder;
import io.trino.spi.function.FunctionMetadata;
import io.trino.spi.function.Signature;
import io.trino.spi.type.CharType;
import io.trino.spi.type.DecimalType;
import io.trino.spi.type.Int128;
import io.trino.spi.type.TimeType;
import io.trino.spi.type.TimestampType;
import io.trino.spi.type.TimestampWithTimeZoneType;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeSignature;
import io.trino.spi.type.VarcharType;

import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.mapWithIndex;
import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.metadata.GlobalFunctionCatalog.builtinFunctionName;
import static io.trino.spi.StandardErrorCode.INVALID_FUNCTION_ARGUMENT;
import static io.trino.spi.function.InvocationConvention.InvocationArgumentConvention.NEVER_NULL;
import static io.trino.spi.function.InvocationConvention.InvocationReturnConvention.FAIL_ON_NULL;
import static io.trino.spi.function.InvocationConvention.simpleConvention;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.Chars.padSpaces;
import static io.trino.spi.type.DateType.DATE;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.Timestamps.roundDiv;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.type.DateTimes.PICOSECONDS_PER_NANOSECOND;
import static io.trino.type.DateTimes.toLocalDateTime;
import static io.trino.type.DateTimes.toZonedDateTime;
import static io.trino.type.JsonType.JSON;
import static io.trino.type.UnknownType.UNKNOWN;
import static io.trino.util.Failures.internalError;
import static io.trino.util.Reflection.methodHandle;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;

public final class FormatFunction
        extends SqlScalarFunction
{
    public static final String NAME = "$format";

    public static final FormatFunction FORMAT_FUNCTION = new FormatFunction();
    private static final MethodHandle METHOD_HANDLE = methodHandle(FormatFunction.class, "sqlFormat", List.class, ConnectorSession.class, Slice.class, Block.class);
    private static final CatalogSchemaFunctionName JSON_FORMAT_NAME = builtinFunctionName("json_format");

    private FormatFunction()
    {
        super(FunctionMetadata.scalarBuilder()
                .signature(Signature.builder()
                        .name(NAME)
                        .variadicTypeParameter("T", "row")
                        .argumentType(VARCHAR.getTypeSignature())
                        .argumentType(new TypeSignature("T"))
                        .returnType(VARCHAR.getTypeSignature())
                        .build())
                .hidden()
                .description("formats the input arguments using a format string")
                .build());
    }

    @Override
    public FunctionDependencyDeclaration getFunctionDependencies(BoundSignature boundSignature)
    {
        FunctionDependencyDeclarationBuilder builder = FunctionDependencyDeclaration.builder();
        boundSignature.getArgumentTypes().get(1).getTypeParameters()
                .forEach(type -> addDependencies(builder, type));
        return builder.build();
    }

    private static void addDependencies(FunctionDependencyDeclarationBuilder builder, Type type)
    {
        if (type.equals(UNKNOWN) ||
                type.equals(BOOLEAN) ||
                type.equals(TINYINT) ||
                type.equals(SMALLINT) ||
                type.equals(INTEGER) ||
                type.equals(BIGINT) ||
                type.equals(REAL) ||
                type.equals(DOUBLE) ||
                type.equals(DATE) ||
                type instanceof TimestampWithTimeZoneType ||
                type instanceof TimestampType ||
                type instanceof TimeType ||
                type instanceof DecimalType ||
                type instanceof VarcharType ||
                type instanceof CharType) {
            return;
        }

        if (type.equals(JSON)) {
            builder.addFunction(JSON_FORMAT_NAME, ImmutableList.of(JSON));
            return;
        }
        builder.addCast(type, VARCHAR);
    }

    @Override
    public SpecializedSqlScalarFunction specialize(BoundSignature boundSignature, FunctionDependencies functionDependencies)
    {
        Type rowType = boundSignature.getArgumentType(1);

        List<Function<Block, Object>> converters = mapWithIndex(
                rowType.getTypeParameters().stream(),
                (type, index) -> converter(functionDependencies, type, toIntExact(index)))
                .collect(toImmutableList());

        return new ChoicesSpecializedSqlScalarFunction(
                boundSignature,
                FAIL_ON_NULL,
                ImmutableList.of(NEVER_NULL, NEVER_NULL),
                METHOD_HANDLE.bindTo(converters));
    }

    @UsedByGeneratedCode
    public static Slice sqlFormat(List<Function<Block, Object>> converters, ConnectorSession session, Slice slice, Block row)
    {
        Object[] args = new Object[converters.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = converters.get(i).apply(row);
        }

        return sqlFormat(session, slice.toStringUtf8(), args);
    }

    private static Slice sqlFormat(ConnectorSession session, String format, Object[] args)
    {
        try {
            return utf8Slice(format(session.getLocale(), format, args));
        }
        catch (IllegalFormatException e) {
            String message = e.toString().replaceFirst("^java\\.util\\.(\\w+)Exception", "$1");
            throw new TrinoException(INVALID_FUNCTION_ARGUMENT, format("Invalid format string: %s (%s)", format, message), e);
        }
    }

    private static Function<Block, Object> converter(FunctionDependencies functionDependencies, Type type, int position)
    {
        Function<Block, Object> converter = valueConverter(functionDependencies, type, position);
        return block -> block.isNull(position) ? null : converter.apply(block);
    }

    private static Function<Block, Object> valueConverter(FunctionDependencies functionDependencies, Type type, int position)
    {
        if (type.equals(UNKNOWN)) {
            return block -> null;
        }
        if (type.equals(BOOLEAN)) {
            return block -> BOOLEAN.getBoolean(block, position);
        }
        if (type.equals(TINYINT)) {
            return block -> (long) TINYINT.getByte(block, position);
        }
        if (type.equals(SMALLINT)) {
            return block -> (long) SMALLINT.getShort(block, position);
        }
        if (type.equals(INTEGER)) {
            return block -> (long) INTEGER.getInt(block, position);
        }
        if (type.equals(BIGINT)) {
            return block -> BIGINT.getLong(block, position);
        }
        if (type.equals(REAL)) {
            return block -> REAL.getFloat(block, position);
        }
        if (type.equals(DOUBLE)) {
            return block -> DOUBLE.getDouble(block, position);
        }
        if (type.equals(DATE)) {
            return block -> LocalDate.ofEpochDay(DATE.getInt(block, position));
        }
        if (type instanceof TimestampWithTimeZoneType timestampWithTimeZoneType) {
            return block -> toZonedDateTime(timestampWithTimeZoneType, block, position);
        }
        if (type instanceof TimestampType timestampType) {
            return block -> toLocalDateTime(timestampType, block, position);
        }
        if (type instanceof TimeType timeType) {
            return block -> toLocalTime(timeType.getLong(block, position));
        }
        // TODO: support TIME WITH TIME ZONE by https://github.com/trinodb/trino/issues/191 + mapping to java.time.OffsetTime
        if (type.equals(JSON)) {
            MethodHandle handle = functionDependencies.getScalarFunctionImplementation(JSON_FORMAT_NAME, ImmutableList.of(JSON), simpleConvention(FAIL_ON_NULL, NEVER_NULL)).getMethodHandle();
            return block -> convertToString(handle, type.getSlice(block, position));
        }
        if (type instanceof DecimalType decimalType) {
            int scale = decimalType.getScale();
            if (decimalType.isShort()) {
                return block -> BigDecimal.valueOf(decimalType.getLong(block, position), scale);
            }
            return block -> new BigDecimal(((Int128) decimalType.getObject(block, position)).toBigInteger(), scale);
        }
        if (type instanceof VarcharType varcharType) {
            return block -> varcharType.getSlice(block, position).toStringUtf8();
        }
        if (type instanceof CharType charType) {
            return block -> padSpaces(charType.getSlice(block, position), charType).toStringUtf8();
        }

        Function<Block, Object> function;
        if (type.getJavaType() == long.class) {
            function = block -> type.getLong(block, position);
        }
        else if (type.getJavaType() == double.class) {
            function = block -> type.getDouble(block, position);
        }
        else if (type.getJavaType() == boolean.class) {
            function = block -> type.getBoolean(block, position);
        }
        else if (type.getJavaType() == Slice.class) {
            function = block -> type.getSlice(block, position);
        }
        else {
            function = block -> type.getObject(block, position);
        }

        MethodHandle handle = functionDependencies.getCastImplementation(type, VARCHAR, simpleConvention(FAIL_ON_NULL, NEVER_NULL)).getMethodHandle();
        return block -> convertToString(handle, function.apply(block));
    }

    private static LocalTime toLocalTime(long value)
    {
        long nanoOfDay = roundDiv(value, PICOSECONDS_PER_NANOSECOND);
        return LocalTime.ofNanoOfDay(nanoOfDay);
    }

    private static Object convertToString(MethodHandle handle, Object value)
    {
        try {
            return ((Slice) handle.invoke(value)).toStringUtf8();
        }
        catch (Throwable t) {
            throw internalError(t);
        }
    }
}
