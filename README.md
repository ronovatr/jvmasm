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
| `examples/BranchDemo.jasm` | labels, ifne, goto |

## Status

- Phase 0: full ISA mnemonic table
- Phase 1–3: lexer/parser/emitter for most opcodes, branches, switches, `.catch`
- Phase 5: disassembler (exact mnemonic preservation)
- Still open: `invokedynamic`, manual `.stack` frames, full CFG stack-merge checking, `jsr`/`ret`
