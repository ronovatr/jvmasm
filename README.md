# jvmasm

Historical-mnemonic JVM bytecode assembler (`.jasm` → `.class`) and disassembler.

Mnemonics match the JVM Specification verbatim (`iload_0`, `ldc_w`, `wide iinc`, …) with **no opcode auto-selection**. Emission uses the JDK **Class-File API** (`java.lang.classfile`, Java 25+).

See `jvm-word-assembler-dev-plan.md` for the full design.

## Requirements

- JDK 25+

## Build / test

```bash
./gradlew test
```

## Assemble / disassemble

```bash
./gradlew run --args="assemble examples/HelloWorld.jasm -o out/HelloWorld.class"
java -cp out HelloWorld

./gradlew run --args="disassemble out/HelloWorld.class"

# Require explicit .stack at every branch/handler target:
./gradlew run --args="assemble examples/StackBranchDemo.jasm --strict-stack -o out/StackBranchDemo.class"
```

## Examples

| File | What it shows |
|------|----------------|
| `examples/HelloWorld.jasm` | getstatic / ldc / invokevirtual |
| `examples/AddDemo.jasm` | bipush + iadd |
| `examples/BranchDemo.jasm` | labels, ifne, goto (auto stack maps) |
| `examples/StackBranchDemo.jasm` | same + manual `.stack` frames |
| `examples/CatchDemo.jasm` | `.catch`, handler frame, `.line` / `.var` |
| `examples/ConstFieldDemo.jasm` | field `ConstantValue` (`= 42`) |
| `examples/IndyDemo.jasm` | `.bootstrap` + `invokedynamic` |
| `examples/SwitchDemo.jasm` | `tableswitch` |
| `examples/LookupDemo.jasm` | `lookupswitch` |
| `examples/ArrayDemo.jasm` | `newarray` / `iaload` / `iastore` |
| `examples/OpcodeDemo.jasm` | exact `iload_0` (not `iload 0`) |
| `examples/ThrowsDemo.jasm` | `.throws` + catch `IOException` |
| `examples/WideDemo.jasm` | `wide iinc` / `wide iload` |

## `.stack` frames

When any method contains `.stack`, the emitter uses `DROP_STACK_MAPS` and writes your frames:

```
Lelse:
    .stack at Lelse locals [Ljava/lang/String; stack
```

Types: `top` `int` `float` `long` `double` `null` `uninitializedThis`, `uninitialized L`, or a class/array descriptor.

Without `.stack`, stack maps are still auto-generated (convenient while learning). Pass `--strict-stack` to require frames at every branch/handler target.

## `invokedynamic`

```
.bootstrap B0 invokestatic java/lang/invoke/StringConcatFactory/makeConcatWithConstants(... )Ljava/lang/invoke/CallSite; "Hello, indy"

invokedynamic makeConcat()Ljava/lang/String; B0
```

## Instruction reference

Every mnemonic below is accepted exactly as spelled (JVMS ch. 6). The assembler never rewrites one opcode into another (`iload 0` stays `iload`; it is **not** turned into `iload_0`).

Stack notation: values left of `→` are popped (top is rightmost); values right of `→` are pushed. Category-2 types (`long`, `double`) occupy two operand-stack slots.

### Constants

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `nop` | — | → | Do nothing. |
| `aconst_null` | — | → `null` | Push `null`. |
| `iconst_m1` | — | → `int` | Push int −1. |
| `iconst_0` | — | → `int` | Push int 0. |
| `iconst_1` | — | → `int` | Push int 1. |
| `iconst_2` | — | → `int` | Push int 2. |
| `iconst_3` | — | → `int` | Push int 3. |
| `iconst_4` | — | → `int` | Push int 4. |
| `iconst_5` | — | → `int` | Push int 5. |
| `lconst_0` | — | → `long` | Push long 0. |
| `lconst_1` | — | → `long` | Push long 1. |
| `fconst_0` | — | → `float` | Push float 0.0. |
| `fconst_1` | — | → `float` | Push float 1.0. |
| `fconst_2` | — | → `float` | Push float 2.0. |
| `dconst_0` | — | → `double` | Push double 0.0. |
| `dconst_1` | — | → `double` | Push double 1.0. |
| `bipush` | `N` | → `int` | Push signed byte (−128…127) as int. Out of range is an error (no upgrade to `sipush`). |
| `sipush` | `N` | → `int` | Push signed short (−32768…32767) as int. |
| `ldc` | literal or pool value | → value | Load constant with **1-byte** CP index. Literals: `"str"`, ints, floats, class names. |
| `ldc_w` | same as `ldc` | → value | Same, but **2-byte** CP index. Distinct opcode — you choose. |
| `ldc2_w` | long/double literal | → `long`/`double` | Load category-2 constant (only form for long/double). |

### Loads (locals)

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `iload` | `N` | → `int` | Load int from local slot `N` (u1, 0–255). |
| `iload_0` | — | → `int` | Load int from slot 0 (1-byte opcode; not interchangeable with `iload 0`). |
| `iload_1` | — | → `int` | Load int from slot 1. |
| `iload_2` | — | → `int` | Load int from slot 2. |
| `iload_3` | — | → `int` | Load int from slot 3. |
| `lload` | `N` | → `long` | Load long from slots `N`/`N+1`. |
| `lload_0` | — | → `long` | Load long from slots 0/1. |
| `lload_1` | — | → `long` | Load long from slots 1/2. |
| `lload_2` | — | → `long` | Load long from slots 2/3. |
| `lload_3` | — | → `long` | Load long from slots 3/4. |
| `fload` | `N` | → `float` | Load float from slot `N`. |
| `fload_0` | — | → `float` | Load float from slot 0. |
| `fload_1` | — | → `float` | Load float from slot 1. |
| `fload_2` | — | → `float` | Load float from slot 2. |
| `fload_3` | — | → `float` | Load float from slot 3. |
| `dload` | `N` | → `double` | Load double from slots `N`/`N+1`. |
| `dload_0` | — | → `double` | Load double from slots 0/1. |
| `dload_1` | — | → `double` | Load double from slots 1/2. |
| `dload_2` | — | → `double` | Load double from slots 2/3. |
| `dload_3` | — | → `double` | Load double from slots 3/4. |
| `aload` | `N` | → `ref` | Load reference from slot `N`. |
| `aload_0` | — | → `ref` | Load reference from slot 0. |
| `aload_1` | — | → `ref` | Load reference from slot 1. |
| `aload_2` | — | → `ref` | Load reference from slot 2. |
| `aload_3` | — | → `ref` | Load reference from slot 3. |

### Loads (arrays)

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `iaload` | — | `array`, `index` → `int` | Load int from int array. |
| `laload` | — | `array`, `index` → `long` | Load long from long array. |
| `faload` | — | `array`, `index` → `float` | Load float from float array. |
| `daload` | — | `array`, `index` → `double` | Load double from double array. |
| `aaload` | — | `array`, `index` → `ref` | Load reference from reference array. |
| `baload` | — | `array`, `index` → `int` | Load byte/boolean from array (sign-extended as int). |
| `caload` | — | `array`, `index` → `int` | Load char from array (zero-extended as int). |
| `saload` | — | `array`, `index` → `int` | Load short from array (sign-extended as int). |

### Stores (locals)

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `istore` | `N` | `int` → | Store int into slot `N`. |
| `istore_0` | — | `int` → | Store int into slot 0. |
| `istore_1` | — | `int` → | Store int into slot 1. |
| `istore_2` | — | `int` → | Store int into slot 2. |
| `istore_3` | — | `int` → | Store int into slot 3. |
| `lstore` | `N` | `long` → | Store long into slots `N`/`N+1`. |
| `lstore_0` | — | `long` → | Store long into slots 0/1. |
| `lstore_1` | — | `long` → | Store long into slots 1/2. |
| `lstore_2` | — | `long` → | Store long into slots 2/3. |
| `lstore_3` | — | `long` → | Store long into slots 3/4. |
| `fstore` | `N` | `float` → | Store float into slot `N`. |
| `fstore_0` | — | `float` → | Store float into slot 0. |
| `fstore_1` | — | `float` → | Store float into slot 1. |
| `fstore_2` | — | `float` → | Store float into slot 2. |
| `fstore_3` | — | `float` → | Store float into slot 3. |
| `dstore` | `N` | `double` → | Store double into slots `N`/`N+1`. |
| `dstore_0` | — | `double` → | Store double into slots 0/1. |
| `dstore_1` | — | `double` → | Store double into slots 1/2. |
| `dstore_2` | — | `double` → | Store double into slots 2/3. |
| `dstore_3` | — | `double` → | Store double into slots 3/4. |
| `astore` | `N` | `ref` → | Store reference into slot `N`. |
| `astore_0` | — | `ref` → | Store reference into slot 0. |
| `astore_1` | — | `ref` → | Store reference into slot 1. |
| `astore_2` | — | `ref` → | Store reference into slot 2. |
| `astore_3` | — | `ref` → | Store reference into slot 3. |

### Stores (arrays)

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `iastore` | — | `array`, `index`, `int` → | Store int into int array. |
| `lastore` | — | `array`, `index`, `long` → | Store long into long array. |
| `fastore` | — | `array`, `index`, `float` → | Store float into float array. |
| `dastore` | — | `array`, `index`, `double` → | Store double into double array. |
| `aastore` | — | `array`, `index`, `ref` → | Store reference into reference array. |
| `bastore` | — | `array`, `index`, `int` → | Store byte/boolean into array. |
| `castore` | — | `array`, `index`, `int` → | Store char into array. |
| `sastore` | — | `array`, `index`, `int` → | Store short into array. |

### Stack manipulation

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `pop` | — | `value` → | Pop one category-1 value. |
| `pop2` | — | `value` → or `v1`, `v2` → | Pop one category-2 or two category-1 values. |
| `dup` | — | `v` → `v`, `v` | Duplicate top category-1 value. |
| `dup_x1` | — | `v2`, `v1` → `v1`, `v2`, `v1` | Insert duplicate under one value. |
| `dup_x2` | — | … → … | Insert duplicate under two category-1 slots (or one category-2). |
| `dup2` | — | … → … | Duplicate top one category-2 or two category-1 values. |
| `dup2_x1` | — | … → … | `dup2` form inserted under one more slot. |
| `dup2_x2` | — | … → … | `dup2` form inserted under two more slots. |
| `swap` | — | `v2`, `v1` → `v1`, `v2` | Swap top two category-1 values. |

### Arithmetic and bitwise

Binary ops pop two operands (right is top) and push the result. Unary `*neg` negate in place.

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `iadd` | — | `int`, `int` → `int` | Add ints. |
| `ladd` | — | `long`, `long` → `long` | Add longs. |
| `fadd` | — | `float`, `float` → `float` | Add floats. |
| `dadd` | — | `double`, `double` → `double` | Add doubles. |
| `isub` | — | `int`, `int` → `int` | Subtract ints (`v1 − v2`). |
| `lsub` | — | `long`, `long` → `long` | Subtract longs. |
| `fsub` | — | `float`, `float` → `float` | Subtract floats. |
| `dsub` | — | `double`, `double` → `double` | Subtract doubles. |
| `imul` | — | `int`, `int` → `int` | Multiply ints. |
| `lmul` | — | `long`, `long` → `long` | Multiply longs. |
| `fmul` | — | `float`, `float` → `float` | Multiply floats. |
| `dmul` | — | `double`, `double` → `double` | Multiply doubles. |
| `idiv` | — | `int`, `int` → `int` | Divide ints (may throw `ArithmeticException`). |
| `ldiv` | — | `long`, `long` → `long` | Divide longs. |
| `fdiv` | — | `float`, `float` → `float` | Divide floats. |
| `ddiv` | — | `double`, `double` → `double` | Divide doubles. |
| `irem` | — | `int`, `int` → `int` | Int remainder. |
| `lrem` | — | `long`, `long` → `long` | Long remainder. |
| `frem` | — | `float`, `float` → `float` | Float remainder. |
| `drem` | — | `double`, `double` → `double` | Double remainder. |
| `ineg` | — | `int` → `int` | Negate int. |
| `lneg` | — | `long` → `long` | Negate long. |
| `fneg` | — | `float` → `float` | Negate float. |
| `dneg` | — | `double` → `double` | Negate double. |
| `ishl` | — | `int`, `int` → `int` | Int left shift (shift count masked to 5 bits). |
| `lshl` | — | `long`, `int` → `long` | Long left shift (count masked to 6 bits). |
| `ishr` | — | `int`, `int` → `int` | Int arithmetic right shift. |
| `lshr` | — | `long`, `int` → `long` | Long arithmetic right shift. |
| `iushr` | — | `int`, `int` → `int` | Int logical right shift. |
| `lushr` | — | `long`, `int` → `long` | Long logical right shift. |
| `iand` | — | `int`, `int` → `int` | Bitwise AND ints. |
| `land` | — | `long`, `long` → `long` | Bitwise AND longs. |
| `ior` | — | `int`, `int` → `int` | Bitwise OR ints. |
| `lor` | — | `long`, `long` → `long` | Bitwise OR longs. |
| `ixor` | — | `int`, `int` → `int` | Bitwise XOR ints. |
| `lxor` | — | `long`, `long` → `long` | Bitwise XOR longs. |
| `iinc` | `N K` | unchanged | Add signed byte `K` to local int at slot `N` (narrow: u1 index, s1 increment). |

### Conversions

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `i2l` | — | `int` → `long` | Int to long. |
| `i2f` | — | `int` → `float` | Int to float. |
| `i2d` | — | `int` → `double` | Int to double. |
| `l2i` | — | `long` → `int` | Long to int (truncate). |
| `l2f` | — | `long` → `float` | Long to float. |
| `l2d` | — | `long` → `double` | Long to double. |
| `f2i` | — | `float` → `int` | Float to int. |
| `f2l` | — | `float` → `long` | Float to long. |
| `f2d` | — | `float` → `double` | Float to double. |
| `d2i` | — | `double` → `int` | Double to int. |
| `d2l` | — | `double` → `long` | Double to long. |
| `d2f` | — | `double` → `float` | Double to float. |
| `i2b` | — | `int` → `int` | Int to byte (truncate + sign-extend). |
| `i2c` | — | `int` → `int` | Int to char (truncate + zero-extend). |
| `i2s` | — | `int` → `int` | Int to short (truncate + sign-extend). |

### Comparisons

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `lcmp` | — | `long`, `long` → `int` | Compare longs: −1 / 0 / 1. |
| `fcmpl` | — | `float`, `float` → `int` | Compare floats; NaN → −1. |
| `fcmpg` | — | `float`, `float` → `int` | Compare floats; NaN → +1. |
| `dcmpl` | — | `double`, `double` → `int` | Compare doubles; NaN → −1. |
| `dcmpg` | — | `double`, `double` → `int` | Compare doubles; NaN → +1. |

### Control transfer

Branch targets are labels (`name:`). Narrow branches use a signed 16-bit offset; `*_w` forms use 32-bit. The assembler does **not** upgrade `goto` to `goto_w`.

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `ifeq` | `LABEL` | `int` → | Branch if == 0. |
| `ifne` | `LABEL` | `int` → | Branch if ≠ 0. |
| `iflt` | `LABEL` | `int` → | Branch if < 0. |
| `ifge` | `LABEL` | `int` → | Branch if ≥ 0. |
| `ifgt` | `LABEL` | `int` → | Branch if > 0. |
| `ifle` | `LABEL` | `int` → | Branch if ≤ 0. |
| `if_icmpeq` | `LABEL` | `int`, `int` → | Branch if ints equal. |
| `if_icmpne` | `LABEL` | `int`, `int` → | Branch if ints not equal. |
| `if_icmplt` | `LABEL` | `int`, `int` → | Branch if `v1 < v2`. |
| `if_icmpge` | `LABEL` | `int`, `int` → | Branch if `v1 ≥ v2`. |
| `if_icmpgt` | `LABEL` | `int`, `int` → | Branch if `v1 > v2`. |
| `if_icmple` | `LABEL` | `int`, `int` → | Branch if `v1 ≤ v2`. |
| `if_acmpeq` | `LABEL` | `ref`, `ref` → | Branch if references equal. |
| `if_acmpne` | `LABEL` | `ref`, `ref` → | Branch if references not equal. |
| `goto` | `LABEL` | unchanged | Unconditional branch (16-bit offset). |
| `goto_w` | `LABEL` | unchanged | Unconditional branch (32-bit offset). |
| `jsr` | `LABEL` | → `retAddr` | Jump subroutine; push return address. **Rejected when class major ≥ 51** (use `.version 50` or lower). |
| `jsr_w` | `LABEL` | → `retAddr` | Wide `jsr` (same version gate). |
| `ret` | `N` | unchanged | Return from subroutine using address in local `N`. Same version gate as `jsr`. |
| `tableswitch` | see below | `int` → | Dense switch. |
| `lookupswitch` | see below | `int` → | Sparse switch (keys must be ascending). |
| `ifnull` | `LABEL` | `ref` → | Branch if reference is `null`. |
| `ifnonnull` | `LABEL` | `ref` → | Branch if reference is not `null`. |

`tableswitch` form:

```
tableswitch default Ldef low 0 high 2 { L0 L1 L2 }
```

`lookupswitch` form:

```
lookupswitch default Ldef { 1 -> L1, 10 -> L10 }
```

### Field and method references

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `getstatic` | `Owner/name Descriptor` | → value | Get static field. |
| `putstatic` | `Owner/name Descriptor` | value → | Set static field. |
| `getfield` | `Owner/name Descriptor` | `obj` → value | Get instance field. |
| `putfield` | `Owner/name Descriptor` | `obj`, value → | Set instance field. |
| `invokevirtual` | `Owner/name(Args)Ret` | `obj`, args… → [ret] | Invoke instance method (virtual). |
| `invokespecial` | `Owner/name(Args)Ret` | `obj`, args… → [ret] | Invoke constructor / private / super. |
| `invokestatic` | `Owner/name(Args)Ret` | args… → [ret] | Invoke static method. |
| `invokeinterface` | `Owner/name(Args)Ret count` | `obj`, args… → [ret] | Invoke interface method; `count` is the argument count byte (nargs + 1 for `this`). |
| `invokedynamic` | `name(Args)Ret Bootstrap` | args… → [ret] | Dynamic call site; `Bootstrap` is a `.bootstrap` name (e.g. `B0`). |

Field example: `getstatic java/lang/System/out Ljava/io/PrintStream;`  
Method example: `invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V`

### Object and array creation / type checks

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `new` | `ClassName` | → `uninitialized` | Allocate object (then `dup` + `invokespecial <init>`). |
| `newarray` | `TYPE` | `count` → `array` | Create primitive array. `TYPE` is one of: `boolean`, `char`, `float`, `double`, `byte`, `short`, `int`, `long`. |
| `anewarray` | `ClassName` | `count` → `array` | Create reference array of component type `ClassName`. |
| `arraylength` | — | `array` → `int` | Array length. |
| `athrow` | — | `throwable` → … | Throw exception (clears stack to handler). |
| `checkcast` | `ClassName` | `ref` → `ref` | Cast reference; throws `ClassCastException` on failure. |
| `instanceof` | `ClassName` | `ref` → `int` | Push 1 if instance of type, else 0 (`null` → 0). |
| `monitorenter` | — | `ref` → | Enter monitor on object. |
| `monitorexit` | — | `ref` → | Exit monitor on object. |
| `multianewarray` | `ClassName dims` | `d1`…`ddims` → `array` | Create multi-dimensional array; `dims` is dimension count. |

Internal class names use `/` separators: `java/lang/String`, `[I`, `[Ljava/lang/Object;`.

### Returns

| Mnemonic | Syntax | Stack | Description |
|----------|--------|-------|-------------|
| `ireturn` | — | `int` → | Return int from method. |
| `lreturn` | — | `long` → | Return long from method. |
| `freturn` | — | `float` → | Return float from method. |
| `dreturn` | — | `double` → | Return double from method. |
| `areturn` | — | `ref` → | Return reference from method. |
| `return` | — | → | Return void. |

### `wide` prefix

`wide` is a real opcode prefix. Write it yourself before a widenable instruction; the assembler never inserts it.

Legal targets: `iload`, `lload`, `fload`, `dload`, `aload`, `istore`, `lstore`, `fstore`, `dstore`, `astore`, `ret`, `iinc`.

```
wide iload 300
wide istore 256
wide iinc 500 10
wide ret 260
```

Using a narrow form with an index > 255 is a hard error.

### Reserved (rejected)

| Mnemonic | Notes |
|----------|-------|
| `breakpoint` | Reserved for debuggers; not legal in ordinary class files. |
| `impdep1` | Implementation-dependent; rejected. |
| `impdep2` | Implementation-dependent; rejected. |

## Status

- Full historical ISA table, assembler, disassembler, round-trip tests
- Branches, switches, `.catch`, `.throws`, `.stack`, `.line`, `.var`, ConstantValue, invokedynamic + `.bootstrap`
- `--strict-stack` / `--no-verify` CLI flags; CFG stack-depth checker (including exception handlers)
- Disassembler emits `.bootstrap`, `.throws`, and exact opcode mnemonics
