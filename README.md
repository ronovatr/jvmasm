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

## `.stack` frames

When any method contains `.stack`, the emitter uses `DROP_STACK_MAPS` and writes your frames:

```
Lelse:
    .stack at Lelse locals [Ljava/lang/String; stack
```

Types: `top` `int` `float` `long` `double` `null` `uninitializedThis`, `uninitialized L`, or a class/array descriptor.

Without `.stack`, stack maps are still auto-generated (convenient while learning).

## `invokedynamic`

```
.bootstrap B0 invokestatic java/lang/invoke/StringConcatFactory/makeConcatWithConstants(... )Ljava/lang/invoke/CallSite; "Hello, indy"

invokedynamic makeConcat()Ljava/lang/String; B0
```

## Status

- Full historical ISA table, assembler, disassembler, round-trip tests
- Branches, switches, `.catch`, `.stack`, `.line`, `.var`, ConstantValue, **invokedynamic**
- Still open: strict “always require `.stack`” mode, richer bootstrap round-trip in disasm
