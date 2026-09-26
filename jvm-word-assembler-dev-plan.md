# Development Plan: A Historical-Mnemonic JVM Bytecode Assembler

**Working name:** `JVMASM` — rename freely.

## 0. Elevator Pitch

A textual, line-oriented assembler for the JVM that uses **the exact historical opcode mnemonics from the JVM Specification, verbatim** — `iload_0`, `invokevirtual`, `ldc_w`, `wide iinc`, `tableswitch`, and so on — with no translation layer, no English-worded renaming, and no paraphrasing of any kind. If you don't already know that `iload_0` and `iload` are two different opcodes, or what `ldc_w` is for, or what `wide` modifies, the language will not teach you or paper over the gap — you must already know JVM internals to write a single valid instruction. The assembler's only job is to encode precisely the opcode named, with precisely the operands given, one-for-one; it never infers, upgrades, or auto-selects between opcodes on the author's behalf.

Locals must be explicitly loaded/stored by raw numeric slot index (no named aliases, no implicit `this`), the operand stack must be explicitly managed, and constant pool entries, exception tables, and stack map frames are all things the author states directly. Assembler-level bookkeeping — resolving a jump target label to a byte offset, or a symbolic `Owner/name Descriptor` reference to a constant-pool index — is retained, because that symbolic-resolution step is the literal, historical job of every assembler that has ever existed (turning names into resolved addresses/indices); it is not a "helper" in the sense being guarded against here. What's explicitly rejected is anything that hides *opcode choice* or *instruction semantics* from the author.

The deliverable is a **compiler (assembler)** that turns `.jasm` source files into valid `.class` files, plus (ideally) a **disassembler** that turns `.class` files back into `.jasm` source, so round-tripping can be used as the primary correctness test.

---

## 1. Goals and Non-Goals

### Goals
- Every JVM instruction from the spec is representable, with no loss of expressiveness versus raw bytecode — including every historical size/shorthand variant of an opcode as its own distinct mnemonic (see §6).
- Mnemonics are **exactly** the strings used in the JVM Specification's instruction set chapter — no renaming, no expansion into English words, no abbreviation-of-abbreviations. `iload_0` is spelled `iload_0`, not `load_local_int_0`.
- **Zero auto-selection.** The assembler never chooses between `iload`/`iload_0`, `bipush`/`sipush`/`ldc`, `goto`/`goto_w`, narrow vs. `wide` forms, or any other historically distinct encoding on the author's behalf. If two real opcodes exist, the language has two real mnemonics, and the author picks.
- Explicit, low-level control: local variable slots, stack depth, constant pool entries, branch targets, exception handlers, and stack map frames are all things the language exposes directly, addressed by raw index/number, never by inferred name or alias.
- Deterministic, well-specified grammar (valid programs can be generated mechanically from the spec below).
- Round-trippable: assemble → disassemble → reassemble should be stable, and should reproduce the *same* mnemonic the original bytecode used (a disassembler that turns `iload_0` back into `iload 0` is a round-trip failure under this design, since it silently discards which real opcode was present).
- Runs on the standard JVM: output `.class` files load and execute in any compliant JVM (verified with `java`, `javap -v`, and `-Xverify:all` initially, later with a bytecode verifier library).

### Non-Goals (at least for v1)
- No high-level control constructs (no `if`, `while`, `for` — those are exactly what a *higher-level* language compiling *to* this assembler would provide later; this project is the target, not the source).
- No macro system, no type inference, no automatic stack-map computation "for free" in v1 (see phased plan — this can be a v2 convenience feature once the manual path works).
- No optimization passes. This is a straight, honest transliteration layer.
- No convenience/helper forms of any kind at the instruction level — not even the "harmless" ones (auto-picking an int-push width, auto-inserting `wide`, named local-slot aliases, English paraphrasing of mnemonics). If it would let the author write or read code without already knowing the exact underlying opcode by its real name, it does not belong in this language.

---

## 2. Prior Art to Study Before/While Building

Read these before generating code, since the JVM class file format and instruction set are exactly and only defined here:
- *The Java Virtual Machine Specification*, chapter 4 (class file format) and chapter 6 (instruction set) — the canonical, authoritative source for every opcode's exact mnemonic spelling, operand encoding, and verification rule. Mnemonic spellings in this project must match chapter 6 character-for-character.
- Jasmin syntax (existing "assembler for the JVM") — the closest prior art: it already uses historical mnemonics verbatim and a similar directive style (`.class`, `.method`, `.limit stack`). Useful as a structural reference for directives; this project's instruction-level fidelity should meet or exceed it (Jasmin does auto-select some encodings, e.g. picking `ldc` vs `ldc_w` automatically — this project should not).
- Krakatau assembler/disassembler — useful prior art for round-trip design and for handling stack map frames explicitly in text form.
- ObjectWeb ASM's `Opcodes`/`Type` classes — useful as a reference implementation detail (see §5 on whether to depend on it) and as a sanity check for opcode values and operand sizes; ASM's opcode field names are themselves the historical mnemonics, which is a convenient cross-check.

---

## 3. High-Level Architecture

```
 .jasm source
      │
      ▼
 ┌───────────┐     ┌───────────┐     ┌────────────────┐     ┌───────────────┐     ┌────────────────┐
 │  Lexer    │ ──▶ │  Parser   │ ──▶ │ Semantic Model  │ ──▶ │ Resolver /     │ ──▶ │ Class File     │
 │ (tokens)  │     │  (AST)    │     │ (typed IR:      │     │ Layout Pass    │     │ Emitter        │
 └───────────┘     └───────────┘     │  classes,       │     │ (const pool,   │     │ (writes .class │
                                      │  fields,        │     │  label→offset, │     │  bytes)        │
                                      │  methods,       │     │  frame calc)   │     └────────────────┘
                                      │  instructions)  │     └────────────────┘
                                      └─────────────────┘
```

Companion, mirror-image pipeline for the disassembler:
```
 .class bytes → ClassReader → Semantic Model → Printer → .jasm source
```

### Java package layout (suggested)

```
com.yourorg.jvmasm
├── cli/                  # entry points: Assemble.main, Disassemble.main
├── lexer/                # Token, TokenType, Lexer, LexException
├── parser/                # Parser, ParseException, Ast.* (sealed node types)
├── ast/                   # ClassDecl, FieldDecl, MethodDecl, Instruction, Directive, Operand...
├── model/                 # Post-parse semantic model: resolved types, symbol tables
├── constpool/              # ConstantPoolBuilder, ConstantPoolEntry (Utf8, Class, NameAndType, Methodref, ...)
├── isa/                    # InstructionSet: table of every opcode ↔ exact historical mnemonic ↔ operand shape
├── emit/                   # ClassFileWriter, MethodBodyWriter, AttributeWriter (Code, StackMapTable, Exceptions...)
├── resolve/                # LabelResolver (branch offsets), FrameComputer (stack map frames), verifier hooks
├── disasm/                 # ClassFileReader, Disassembler/Printer
└── util/                   # BigEndianDataOutput, Varint helpers, etc.
```

---

## 4. Language Design

### 4.1 Lexical rules
- Line-oriented, whitespace/tab-insensitive within a line, one instruction or directive per line.
- Comments: `;` to end of line (Jasmin-style) — pick one and stick to it; avoid `//` collision with nothing else needed.
- Identifiers: `[A-Za-z_$][A-Za-z0-9_$]*`; fully-qualified class names use `/`-separated internal form directly (`java/lang/Object`) to mirror the class file format exactly — no dot-to-slash translation magic.
- Labels: `name:` at start of line, referenced elsewhere as bare `name`.
- Literals: decimal/hex integers (`0x` prefix), floating literals with explicit suffix (`1.0f`, `1.0d`), string literals in double quotes with standard escapes.
- Mnemonics are matched **case-sensitively against the exact spelling in the JVM Specification** (all lowercase, underscores exactly where the spec has them — `if_icmpeq`, not `ifIcmpEq` or `if_ICMP_EQ`). Directives (which are assembler-level constructs, not opcodes, and therefore have no "historical spelling" to preserve) use a `.`-prefixed, lowercase convention of this project's own choosing. Identifiers (class/field/method names) preserve case as given, since they name real JVM symbols.

### 4.2 File/Class structure (directives)

One `.jasm` file = one class (mirrors one `.class` file). Directives are assembler bookkeeping, not opcodes, so they are not subject to the "must match the spec's mnemonic" rule — this project follows Jasmin's directive conventions closely since that convention is already well-established prior art:

| Directive | Purpose | Example |
|---|---|---|
| `.class` | class access flags + internal name | `.class public final MyProgram` |
| `.super` | superclass internal name | `.super java/lang/Object` |
| `.implements` | one interface per line | `.implements java/lang/Runnable` |
| `.source` | SourceFile attribute | `.source "MyProgram.jasm"` |
| `.field` | field declaration | `.field private static I counter` |
| `.field ... = value` | field with ConstantValue attribute | `.field public static final I MAX = 100` |
| `.method` ... `.end method` | method body block | see below |
| `.limit stack N` | max operand stack for enclosing method | `.limit stack 4` |
| `.limit locals N` | max locals for enclosing method | `.limit locals 3` |
| `.var` | local variable table entry (debug info only — never a usable operand alias, see §4.3) | `.var 0 is this Lcom/foo/Bar; from L0 to L1` |
| `.line` | LineNumberTable entry | `.line 42` |
| `.catch` | exception table entry | `.catch java/lang/Exception from L0 to L1 using L2` |
| `.throws` | Exceptions attribute entry | `.throws java/io/IOException` |
| `.stack` | explicit stack map frame declaration at a label (v1: required; v2: optional/auto) | `.stack use locals stack Object` |
| `.const` | pre-declare a constant pool entry explicitly, if the author wants to pin a specific index | `.const #1 = Methodref java/lang/Object <init> ()V` |
| `.annotation` | (stretch) annotation attribute | — |

Method header syntax, using the real opcode spellings throughout:
```
.method public static main([Ljava/lang/String;)V
    .limit stack 2
    .limit locals 1
        getstatic java/lang/System/out Ljava/io/PrintStream;
        ldc "Hello, world"
        invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V
        return
.end method
```

Descriptors (`([Ljava/lang/String;)V`, `Ljava/io/PrintStream;`) are taken **verbatim in JVM descriptor syntax** — do not invent a friendlier type syntax; the whole point is staying low-level and 1:1 with the spec.

### 4.3 Explicitness principle (the core design constraint)
Every stack effect is manual. There is no "call method with these argument expressions" sugar — the author must push the receiver, push each argument in order, then emit the invoke instruction, exactly as raw bytecode requires. Locals are addressed **only** by raw numeric slot index (`iload 0`) — no named aliases, no symbolic slot names, no `this` keyword standing in for slot 0. `.var` directives may still exist purely to emit debug-info attributes (`LocalVariableTable`) into the output class, but they are write-only documentation for downstream tools like debuggers — never readable back as operands in load/store instructions.

Every mnemonic the author writes must be one of the exact opcode spellings from JVM Specification chapter 6, spelled exactly as the spec spells it. There is no separate "friendly name" for any opcode.

---

## 5. Key Engineering Decision: Hand-Roll the Class Writer, or Depend on ASM?

Two honest options — decide early and document the choice:

1. **Hand-roll everything** (constant pool builder, `Code` attribute writer, `StackMapTable` writer, big-endian binary I/O). More work, but matches the "as low-level as possible" ethos and is the better learning/teaching artifact. Recommended if the project's purpose is understanding the class file format, not just shipping something quickly.
2. **Use ObjectWeb ASM's `ClassWriter`/`MethodVisitor` as the emission backend**, and write only the front end (lexer/parser/assembler logic) yourself, calling `mv.visitVarInsn`, `mv.visitMethodInsn`, etc. Much faster to a working v1, and you get `COMPUTE_FRAMES`/`COMPUTE_MAXS` for free if you want it later.

**Recommendation:** build the front end (lexer → parser → IR) fully hand-rolled and back-end-agnostic (an `Emitter` interface), then implement **two** emitter backends in parallel or in sequence: `RawBytecodeEmitter` (hand-rolled, for learning/fidelity) and `AsmEmitter` (thin wrapper over ASM, for a fast working reference/test oracle). Use the ASM-backed emitter as a correctness oracle for the hand-rolled one — note that ASM's own auto-selecting convenience methods (e.g. `visitLdcInsn` picking `ldc` vs `ldc_w` for you) must **not** be relied on directly if you want this project's own front end to retain author-specified opcode choice; call the more explicit ASM APIs, or bypass them for anything ASM would auto-select.

---

## 6. The Instruction Set — Exact Historical Mnemonics

Every row below is copied from JVM Specification chapter 6 verbatim. This table **is** the core spec the assembler's `isa/` package should encode 1:1 (e.g. as an enum with fields: opcode byte, mnemonic string, operand descriptor, stack effect). There is no "worded" or "friendly" column — the mnemonic column below **is** what the author types.

### 6.1 Constants
| Opcode | Operand syntax | Notes |
|---|---|---|
| `nop` | — | |
| `aconst_null` | — | |
| `iconst_m1` | — | |
| `iconst_0` | — | |
| `iconst_1` | — | |
| `iconst_2` | — | |
| `iconst_3` | — | |
| `iconst_4` | — | |
| `iconst_5` | — | |
| `lconst_0` | — | |
| `lconst_1` | — | |
| `fconst_0` | — | |
| `fconst_1` | — | |
| `fconst_2` | — | |
| `dconst_0` | — | |
| `dconst_1` | — | |
| `bipush` | `N` | signed byte, -128..127 |
| `sipush` | `N` | signed short, -32768..32767 |
| `ldc` | `N` or a literal (`"str"`, class name, numeric constant) | 1-byte constant pool index; author's job to know the index fits u1, or use `ldc_w` |
| `ldc_w` | same as `ldc` | 2-byte constant pool index |
| `ldc2_w` | long/double literal or index | 2-byte index; only form that exists for category-2 constants |

The author decides for themselves which of `iconst_*`/`bipush`/`sipush`/`ldc` fits a given int value — the assembler does not choose for them, and errors rather than switching forms if what's written can't be encoded as written (e.g. `bipush 300` is a hard error: out of range for a signed byte).

### 6.2 Loads
| Opcode | Operand syntax |
|---|---|
| `iload` | `N` (u1 index, 0–255) |
| `iload_0` | — |
| `iload_1` | — |
| `iload_2` | — |
| `iload_3` | — |
| `lload` | `N` |
| `lload_0` | — |
| `lload_1` | — |
| `lload_2` | — |
| `lload_3` | — |
| `fload` | `N` |
| `fload_0` | — |
| `fload_1` | — |
| `fload_2` | — |
| `fload_3` | — |
| `dload` | `N` |
| `dload_0` | — |
| `dload_1` | — |
| `dload_2` | — |
| `dload_3` | — |
| `aload` | `N` |
| `aload_0` | — |
| `aload_1` | — |
| `aload_2` | — |
| `aload_3` | — |
| `iaload` | — |
| `laload` | — |
| `faload` | — |
| `daload` | — |
| `aaload` | — |
| `baload` | — |
| `caload` | — |
| `saload` | — |

`iload` and `iload_0` are different opcodes with different byte encodings and the author picks which to write; the assembler does not rewrite `iload 0` into `iload_0` or vice versa.

### 6.3 Stores
| Opcode | Operand syntax |
|---|---|
| `istore` | `N` |
| `istore_0` | — |
| `istore_1` | — |
| `istore_2` | — |
| `istore_3` | — |
| `lstore` | `N` |
| `lstore_0` | — |
| `lstore_1` | — |
| `lstore_2` | — |
| `lstore_3` | — |
| `fstore` | `N` |
| `fstore_0` | — |
| `fstore_1` | — |
| `fstore_2` | — |
| `fstore_3` | — |
| `dstore` | `N` |
| `dstore_0` | — |
| `dstore_1` | — |
| `dstore_2` | — |
| `dstore_3` | — |
| `astore` | `N` |
| `astore_0` | — |
| `astore_1` | — |
| `astore_2` | — |
| `astore_3` | — |
| `iastore` | — |
| `lastore` | — |
| `fastore` | — |
| `dastore` | — |
| `aastore` | — |
| `bastore` | — |
| `castore` | — |
| `sastore` | — |

### 6.4 Stack manipulation
| Opcode | Operand syntax |
|---|---|
| `pop` | — |
| `pop2` | — |
| `dup` | — |
| `dup_x1` | — |
| `dup_x2` | — |
| `dup2` | — |
| `dup2_x1` | — |
| `dup2_x2` | — |
| `swap` | — |

### 6.5 Math
| Opcode | Operand syntax |
|---|---|
| `iadd` / `ladd` / `fadd` / `dadd` | — |
| `isub` / `lsub` / `fsub` / `dsub` | — |
| `imul` / `lmul` / `fmul` / `dmul` | — |
| `idiv` / `ldiv` / `fdiv` / `ddiv` | — |
| `irem` / `lrem` / `frem` / `drem` | — |
| `ineg` / `lneg` / `fneg` / `dneg` | — |
| `ishl` / `lshl` | — |
| `ishr` / `lshr` | — |
| `iushr` / `lushr` | — |
| `iand` / `land` | — |
| `ior` / `lor` | — |
| `ixor` / `lxor` | — |
| `iinc` | `N K` (narrow form: u1 index, s1 increment — see `wide iinc` in §6.11 for the wide form) |

### 6.6 Conversions
| Opcode | Operand syntax |
|---|---|
| `i2l` `i2f` `i2d` `l2i` `l2f` `l2d` `f2i` `f2l` `f2d` `d2i` `d2l` `d2f` `i2b` `i2c` `i2s` | — |

### 6.7 Comparisons
| Opcode | Operand syntax |
|---|---|
| `lcmp` | — |
| `fcmpl` / `fcmpg` | — |
| `dcmpl` / `dcmpg` | — |

### 6.8 Control transfer
| Opcode | Operand syntax | Notes |
|---|---|---|
| `ifeq` `ifne` `iflt` `ifge` `ifgt` `ifle` | `LABEL` | signed 16-bit offset |
| `if_icmpeq` `if_icmpne` `if_icmplt` `if_icmpge` `if_icmpgt` `if_icmple` | `LABEL` | |
| `if_acmpeq` `if_acmpne` | `LABEL` | |
| `goto` | `LABEL` | signed 16-bit offset; author's job to confirm range or use `goto_w` |
| `goto_w` | `LABEL` | signed 32-bit offset; a distinct opcode |
| `jsr` | `LABEL` | pushes return address; **illegal in class files with major version ≥ 51** (Java 7+) — the assembler must accept it only for target versions the author explicitly selects that permit it, and error otherwise |
| `jsr_w` | `LABEL` | same legality note as `jsr` |
| `ret` | `N` | u1 local slot holding return address (see `wide ret` in §6.11) |
| `tableswitch` | `default LABEL low LOW high HIGH { LABEL, LABEL, ... }` | one label per case from LOW to HIGH inclusive |
| `lookupswitch` | `default LABEL { KEY -> LABEL, KEY -> LABEL, ... }` | pairs must be written in ascending key order per the class file format's requirement |
| `ifnull` / `ifnonnull` | `LABEL` | |

### 6.9 References
| Opcode | Operand syntax |
|---|---|
| `getstatic` | `Owner/name Descriptor` |
| `putstatic` | `Owner/name Descriptor` |
| `getfield` | `Owner/name Descriptor` |
| `putfield` | `Owner/name Descriptor` |
| `invokevirtual` | `Owner/name(Descriptor)ReturnType` |
| `invokespecial` | `Owner/name(Descriptor)ReturnType` |
| `invokestatic` | `Owner/name(Descriptor)ReturnType` |
| `invokeinterface` | `Owner/name(Descriptor)ReturnType argcount` |
| `invokedynamic` | `name(Descriptor)ReturnType bootstrap_method_ref` (see §8 stretch goals — genuinely complex) |
| `new` | `ClassName` |
| `newarray` | `TYPE` (one of `boolean char float double byte short int long`, encoded to the correct `atype` byte per JVM spec table 6.6 — `boolean`=4, `char`=5, `float`=6, `double`=7, `byte`=8, `short`=9, `int`=10, `long`=11) |
| `anewarray` | `ClassName` |
| `arraylength` | — |
| `athrow` | — |
| `checkcast` | `ClassName` |
| `instanceof` | `ClassName` |
| `monitorenter` | — |
| `monitorexit` | — |
| `multianewarray` | `ClassName dims` |

### 6.10 Method returns
| Opcode | Operand syntax |
|---|---|
| `ireturn` / `lreturn` / `freturn` / `dreturn` / `areturn` | — |
| `return` | — |

### 6.11 The `wide` prefix opcode
`wide` (opcode `0xC4`) is a real, standalone opcode: it is written immediately before `iload`, `lload`, `fload`, `dload`, `aload`, `istore`, `lstore`, `fstore`, `dstore`, `astore`, `ret`, or `iinc`, and widens that instruction's index (and, for `iinc` only, also its increment) from a 1-byte operand to a 2-byte operand. This language exposes `wide` exactly as what it is — a literal prefix token the author writes themselves — and never auto-inserts it, including when a narrow-form instruction is given an index the narrow encoding can't hold; that case is a hard assembly error ("index N exceeds u1 range for iload; prefix with wide"), never a silent upgrade.

```
wide iload 300
wide astore 400
wide ret 260
wide iinc 500 10
```

The parser accepts `wide` only directly preceding one of the eleven opcodes above (the only ones `wide` is defined to modify per the spec) and rejects it before anything else as a hard error.

### 6.12 Reserved
- `breakpoint`, `impdep1`, `impdep2` — reserved for JVM debuggers/implementations, not legal in ordinary class files; the assembler should reject them if written.

> Deliverable for this section: encode the whole table above as data (e.g. a Java `enum InstructionDef` keyed by the exact mnemonic string, or a resource file), not as scattered `if` chains — this becomes the single source of truth used by lexer keyword recognition, the parser's operand-shape validation, the emitter's opcode+operand encoding, and the disassembler's printer, so it can never drift out of sync across the four subsystems, and so the mnemonic spelling can be diffed directly against JVM Specification chapter 6 during review.

---

## 7. Semantics to Nail Down Precisely

Write these as explicit rules in the spec doc before coding, since they're exactly the kind of thing that's easy to get subtly wrong:

1. **Local variable slots.** Category 2 types (`long`, `double`) occupy two consecutive slots. `.limit locals` must equal the highest slot index used + 1 (or +2 for the last if it's category 2) — the assembler should validate this and error, not silently patch it. Slots are addressed only by raw numeric index everywhere (§4.3) — no aliases, no named slots, no implicit `this`. `iload_0` is a hard error if the author actually needs slot 4 (they must write `iload 4`); `iload 0..3` for slots 0–3 is a legal, larger encoding of the same runtime effect but the assembler never rewrites it to the shorthand form on its own, and vice versa.
2. **Operand stack depth tracking.** `.limit stack` is author-declared, but the assembler should still simulate stack depth per basic block at assemble time and error on overflow/underflow/mismatched merge depths — this is your first real "verifier-lite" pass and will catch most authoring mistakes before they become a JVM `VerifyError` at class-load time.
3. **Branch offsets.** All labels are resolved in a fixup pass after a first pass assigns provisional byte offsets to each instruction; because instruction *sizes* can differ depending on which literal opcode the author chose (`goto` is 3 bytes, `goto_w` is 5; a narrow `iload` is 2 bytes, `wide iload` is 4), verify offsets in a fixed point rather than assuming one pass suffices, and — critically — never let this resolution pass change *which opcode* the author wrote; it only computes the byte value of the offset operand for the opcode as given.
4. **Stack map frames.** Required for class file major version ≥ 50 (Java 6+) whenever a branch target has more than one predecessor state, per the verifier's type-checking rules. Recommended path: **manual `.stack` directives required in v1** (matches the project's spirit), automatic computation as a v2 opt-in flag.
5. **Constant pool deduplication.** By default the assembler should deduplicate identical constant pool entries (two `ldc "x"` calls should share one pool slot) unless the author used `.const` to pin explicit slots, in which case respect exactly what's declared. This is standard assembler symbol-table behavior, not an instruction-level helper, and stays in scope.
6. **Descriptor syntax.** Accept full JVM descriptor grammar for fields/methods/classes exactly as specified (chapter 4.3 of the spec) with no simplification.
7. **Access flags.** Every `.class`/`.field`/`.method` directive takes the exact JVM access flag keywords (`public`, `private`, `protected`, `static`, `final`, `synchronized`, `native`, `abstract`, `interface`, `volatile`, `transient`, `synthetic`, `enum`, `bridge`, `varargs`) with no aliasing.
8. **Class file version.** `.class` directive (or a separate `.version` directive) should let the author pick the target major/minor version, since several of the above rules (stack map frames, `jsr`/`ret` legality) are version-gated.

---

## 8. Phased Delivery Plan

**Phase 0 — Spec lock-in.** Finalize this document into a machine-readable instruction table (`isa/InstructionSet.java` or a JSON/CSV resource) covering every opcode in §6, with mnemonic strings checked character-for-character against JVM Specification chapter 6. Do this before writing any parser code — it's the contract every other phase depends on.

**Phase 1 — Skeleton toolchain, minimal opcode subset.** Lexer + parser + AST for just: `.class`, `.super`, `.method`/`.end method`, `.limit stack/locals`, `return`, `getstatic`, `ldc`, `invokevirtual`. Emit via the ASM-backed emitter first (fastest path to a runnable "Hello, world"). Success criterion: a `.jasm` Hello World assembles and runs with `java`.

**Phase 2 — Full instruction coverage.** Implement every row of §6 in the parser/IR/emitter, including every `_0`..`_3` shorthand and every width variant as its own opcode. Add the stack-depth simulator (§7.2) as a validation pass.

**Phase 3 — Control flow completeness.** Labels, `goto`/`goto_w`, all `if*`/`if_icmp*`/`if_acmp*`/`ifnull`/`ifnonnull`, `tableswitch`, `lookupswitch`, exception tables (`.catch`), and the fixed-point branch-offset resolver (§7.3).

**Phase 4 — Stack map frames + hand-rolled emitter.** Implement `.stack` directive parsing/emission, and build the `RawBytecodeEmitter` (hand-rolled binary writer for the constant pool, `Code` attribute, `StackMapTable`, `LineNumberTable`, `LocalVariableTable`, `Exceptions`) as a second backend, cross-checked against the ASM-backed emitter's output.

**Phase 5 — Disassembler.** `ClassFileReader` + printer that regenerates `.jasm` source from arbitrary `.class` files (including ones not produced by this tool, e.g. `javac` output), proving the mnemonic table is truly bijective with real-world bytecode — including preserving which exact shorthand/width opcode was present (e.g. `iload_0` must disassemble back to `iload_0`, never `iload 0`). Round-trip test: disassemble a `javac`-compiled class, reassemble it, diff behavior (and ideally bytes) against the original.

**Phase 6 — Diagnostics & tooling polish.** Good error messages with source line/column, a `--verify` flag that runs the class through a real verifier (or `-Xverify:all` subprocess) automatically after assembly, a CLI (`jvmasm assemble Foo.jasm`, `jvmasm disassemble Foo.class`), and a test suite of representative programs (arithmetic, loops via manual branches, arrays, exceptions, interfaces, static/instance methods, recursion).

**Phase 7 (stretch) — `invokedynamic` / `MethodHandle`/bootstrap support, annotations, automatic stack-map computation as an opt-in convenience mode, and a small examples folder.**

---

## 9. Testing Strategy

- **Golden-file tests:** a directory of `.jasm` sample programs with expected stdout when run, expected `javap -v` structural output, or expected exit codes.
- **Differential testing:** for every sample, assemble with both the ASM-backed and hand-rolled emitters and assert the resulting classes are behaviorally identical.
- **Round-trip testing:** disassemble real-world `.class` files (JDK classes, `javac` output of small test programs) → reassemble → confirm the JVM still loads and runs them identically, and that the specific opcode variants used (e.g. `iload_0` vs `iload`) are preserved exactly.
- **Verifier testing:** run `java -Xverify:all` (or embed a verifier) on every generated class as part of CI.
- **Unit tests per instruction:** for each row in §6, a minimal method body using just that instruction, with an assertion on the resulting opcode byte(s) and operand encoding, cross-checked against JVM Specification chapter 6.

---

## 10. Suggested First Milestone Checklist (copy/paste into an issue tracker)

- [ ] Lock instruction table as data (`isa/`), covering every opcode above, mnemonic strings verified against the spec.
- [ ] Lexer: tokens for directives, mnemonics, identifiers, literals, labels, comments.
- [ ] Parser: `.class`/`.super`/`.method`/`.end method`/`.limit` + a handful of instructions.
- [ ] AST → IR: resolve label references within a method body.
- [ ] ASM-backed emitter for the Phase 1 subset.
- [ ] CLI: `jvmasm assemble HelloWorld.jasm -o HelloWorld.class`.
- [ ] Manually verify `java HelloWorld` prints as expected.
- [ ] Add stack-depth simulator + first real error messages.
- [ ] Expand instruction coverage to 100% of §6.
- [ ] Add branch/label fixed-point resolver + `.catch`.
- [ ] Add `.stack` + hand-rolled emitter.
- [ ] Build disassembler; round-trip a `javac`-compiled class, confirming exact opcode preservation.

---

## 11. Notes for Whoever (or Whatever) Implements This

- Treat §6's table as the ground truth, checked directly against JVM Specification chapter 6 — every mnemonic string in the codebase should be traceable to a specific row in the spec, spelled identically.
- **There are no convenience exceptions in this language, none at all.** Every historically distinct opcode — every `_0`/`_1`/`_2`/`_3` shorthand, every width variant (`bipush`/`sipush`/`ldc`/`ldc_w`/`ldc2_w`, `goto`/`goto_w`, `jsr`/`jsr_w`), and the `wide` prefix itself — is a separate, explicit mnemonic the author must choose correctly, spelled exactly as the JVM spec spells it. If two real opcodes exist for a situation, the language must expose two real mnemonics under their real names and must never infer which one to emit, and must never substitute an English paraphrase for the real name. The author is required to already understand the JVM instruction set to write or read a single valid instruction — that is the entire point of this project.
- Get the class file format spec's exact byte layouts right (constant pool tag bytes, attribute name/length framing, `u1`/`u2`/`u4` field widths) — this is unforgiving; a single misordered field silently produces a corrupt class file that some tools tolerate and others don't.
