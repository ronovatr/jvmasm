# jvmasm

Historical-mnemonic JVM bytecode assembler (`.jasm` → `.class`).

Mnemonics match the JVM Specification verbatim (`iload_0`, `ldc_w`, `wide iinc`, …) with **no opcode auto-selection**. Emission uses the JDK **Class-File API** (`java.lang.classfile`, Java 25+).

See `jvm-word-assembler-dev-plan.md` for the full design.

## Requirements

- JDK 25+

## Build / run

```bash
./gradlew run --args="assemble examples/HelloWorld.jasm -o out/HelloWorld.class"
java -cp out HelloWorld
```

## Status

Scaffold + Phase 0 ISA table + Phase 1 lexer/parser/emitter in progress.
