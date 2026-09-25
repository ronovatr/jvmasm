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

## `.stack` frames

When any method contains `.stack`, the emitter uses `DROP_STACK_MAPS` and writes your frames:

```
Lelse:
    .stack at Lelse locals [Ljava/lang/String; stack
```

Types: `top` `int` `float` `long` `double` `null` `uninitializedThis`, `uninitialized L`, or a class/array descriptor.

Without `.stack`, stack maps are still auto-generated (convenient while learning).

## Status

- Phase 0–3: ISA, lexer/parser/emitter, branches, switches, `.catch`
- Phase 4: manual `.stack` + CFG stack-depth merge checks
- Phase 5: disassembler (`.stack` / `.catch` / fields) + round-trip
- Debug attrs: `.line`, `.var`; field `ConstantValue`
- Still open: `invokedynamic`, strict “always require `.stack`” mode