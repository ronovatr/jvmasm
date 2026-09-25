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

## Status

- Full historical ISA table, assembler, disassembler, round-trip tests
- Branches, switches, `.catch`, `.throws`, `.stack`, `.line`, `.var`, ConstantValue, invokedynamic + `.bootstrap`
- `--strict-stack` / `--no-verify` CLI flags; CFG stack-depth checker (including exception handlers)
- Disassembler emits `.bootstrap`, `.throws`, and exact opcode mnemonics
