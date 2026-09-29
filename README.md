# SlimEnum + SlimStruct

<!-- Plugin description -->
Java's value-type story is threadbare. Enums allocate; bit-packed primitives are invisible to the IDE. This plugin closes both gaps without asking the
runtime to change.

**SlimEnum** turns an `@interface` of primitive or `String` constants into a type-bound set. At every site annotated with it — variable, field,
parameter, return type — IntelliJ offers completion restricted to that exact set.

**SlimStruct** turns an `@interface` that holds groups of static accessors — `get`, `set`, `hasValue`, `to_null`, `New` — into a named record living
entirely inside a single `byte`, `char`, `short`, `int`, or `long`. Packs propagate implicitly through assignments and casts. Dot-access on a packed
variable lists the accessors of every field as a completion; picking one rewrites `pack.` into `Struct.get.field(pack)`, and a writer is assigned
back: `pack = Struct.set.field(pack, value)`. A slot that takes a pack offers what makes one: `Struct.New.of(…)`.

The same works in **TypeScript**, for a `number` under a type alias whose namespace holds the same groups.

Both features exist entirely in the IDE. The emitted code is always plain primitives — zero allocation, zero indirection, zero runtime magic.
<!-- Plugin description end -->

The two features address different problems. Skip to whichever applies:

- **[SlimEnum](#slimenum--type-bound-constant-sets)** — you're tired of `enum` objects and want primitive constants with the editor experience of
  enums.
- **[SlimStruct](#slimstruct--packed-records-as-opaque-types)** — you want C#-`struct`-style named value records, but Java has no `struct`, no opaque
  types, and no value classes. [TypeScript](#slimstruct-in-typescript) has none either.

---

## SlimEnum — type-bound constant sets

### 1. Declare a SlimEnum annotation

Create an `@interface` and put `static final` constants of the same primitive type (or `String`) inside it. Nest annotations to group related sets.

```java
@interface Font {
	// byte constants — treated as FLAGS (see "Enum vs flag" below)
	byte NORMAL       = 0,
			BOLD      = 1,
			UNDERLINE = 3,
			BLINK     = 4,
			INVERSE   = 5,
			STRIKE    = 6;
	
	// String constants — always treated as enum-like (mutually exclusive)
	String Helvetica   = "Helvetica",
			Palatino   = "Palatino",
			HonMincho  = "HonMincho",
			Serif      = "Serif",
			Monospaced = "Monospaced",
			Dialog     = "Dialog";
	
	@interface Foreground {
		// first two names "RED" > "BLACK" → FLAG-style completion
		int RED         = 1,
				BLACK   = 0,
				GREEN   = 2,
				YELLOW  = 3,
				BLUE    = 4,
				MAGENTA = 5,
				CYAN    = 6,
				WHITE   = 7,
				DEFAULT = 8;
		
		// a plain utility class can live inside the annotation
		class Validator {
			public static boolean ok( int value ) {
				switch( value ) {
					case BLACK: case RED: case GREEN: case YELLOW:
					case BLUE: case MAGENTA: case CYAN: case WHITE:
					case DEFAULT:
						return true;
				}
				return false;
			}
		}
	}
	
	@interface Background {
		// first two names "BLACK" < "RED" → ENUM-style completion
		int BLACK           = 0,
				RED         = 1,
				GREEN       = 2,
				YELLOW      = 3,
				BLUE        = 4,
				MAGENTA     = 5,
				CYAN        = 6,
				WHITE       = 7,
				DEFAULT     = 8,
				TRANSPARENT = 0; // aliases are fine — values don't have to be unique
	}
}
```

### 2. Enum vs flag behaviour

SlimEnum picks an exclusion policy for its completion popup from the **alphabetical order of the first two declared constant names** — nothing else.
It never looks at the values.

| First two names               | Treated as | What completion filters out                                   |
|-------------------------------|------------|---------------------------------------------------------------|
| `A, B` (alphabetical order)   | **enum**   | constants already used as `case` labels in the same `switch`  |
| `B, A` (any non-alphabetical) | **flags**  | constants already OR-ed (`\|`) into the current expression    |
| any order, `String` type      | **enum**   | same as enum above — non-primitive types are always enum-like |

### 3. Apply the annotation and use the completion

```java
class Test {
	@Font            String name;
	@Font.Foreground int    fg;
	
	@Font.Background int setBackground( @Font.Background int bg ) { return bg; }
	
	static void createFont( @Font String name,
	                        @Font byte style,
	                        @Font.Background int background,
	                        @Font.Foreground int foreground ) { }
}
```

At every position whose type is covered by a SlimEnum annotation, completion lists exactly the matching constants — qualified, type-filtered, and
policy-aware.
![img_3.png](img_3.png)

second argument

![img_4.png](img_4.png)

in a switch case

![img_5.png](img_5.png)
---

## SlimStruct — packed records as opaque types

### The heap problem with small objects

Every non-primitive in Java lives on the heap. A `Point { int x, y }` is 16–24 bytes of object header plus 8 bytes of data, reached through a pointer.
Scale to millions:

- **2–3× the memory** the raw data actually needs;
- **GC pressure** proportional to population;
- **Cache misses** on every iteration — each field read chases a pointer.

For dense simulation state, packet headers, permission masks, or grid coordinates, the overhead dwarfs the payload.

### How other languages solve it

| Language  | Construct                                                                                     | Guaranteed unboxed?                                        |
|-----------|-----------------------------------------------------------------------------------------------|------------------------------------------------------------|
| C#        | [`struct`](https://learn.microsoft.com/dotnet/csharp/language-reference/builtin-types/struct) | yes — stack or inline in arrays                            |
| Scala 3   | [opaque type](https://docs.scala-lang.org/scala3/book/types-opaque-types.html)                | **yes — always**                                           |
| Scala 2/3 | [value class](https://docs.scala.org/overviews/core/value-classes.html)                       | no — boxes in `Any` slots, arrays, generics, pattern match |
| Kotlin    | [inline / value class](https://kotlinlang.org/docs/inline-classes.html)                       | usually — boxes in generics and nullable contexts          |
| Rust / Go | native `struct`                                                                               | yes                                                        |

The sharpest line is drawn by Scala 3, between *opaque types* and *value classes*:

> An **opaque type** is always just the underlying type at runtime. There is no wrapper class at all, so there is no scenario — generics, arrays,
> pattern matching, `Any`-typed slots — where boxing can sneak in.
>
> A **value class** *may* box. The compiler inserts a wrapper as soon as you put the value somewhere polymorphism is required. The zero-allocation
> guarantee only holds in monomorphic code paths.

Java offers neither. [Project Valhalla](https://openjdk.org/projects/valhalla/) intends to eventually deliver value classes to the platform, but
nothing has landed in any current LTS — see the
running [state-of-Valhalla document](https://openjdk.org/projects/valhalla/design-notes/state-of-valhalla/01-background) for the scope of what Java
still lacks today.

### How Java can do it today — and why nobody does

A Java primitive holds up to **64 bits**. That is enough for:

- nine Unix permission flags in a `short` (16 bits)
- four IPv4 octets in an `int` (32 bits)
- a 40-bit timestamp + 8-bit event type + 16-bit sequence in a `long` (64 bits)
- 64 independent booleans in a single `long`

The layout is just bit masks and shifts. Zero allocation, zero indirection, the value is literally the primitive — matching Scala 3 opaque types'
guarantee, not Scala 2 value classes' "sometimes boxes" one.

Writing it by hand, however, is miserable:

- `mode |= 0x40` tells the IDE nothing — no completion, no field name, no range check.
- `(mode >>> 16) & 0xFF` looks identical regardless of *which* field you meant.
- Bit-layout mistakes compile silently and surface as corrupt data at runtime.
- Refactoring a bit offset means visiting every call site, blind.

The savings are real. The maintenance burden is why the pattern rarely leaves hot-path libraries that can afford a dedicated maintainer.

### A naive first attempt — plain `interface` with static accessors

The obvious cleanup is to give each field's bit codec a name. Wrap the four IPv4 octets in a plain Java `interface`, the readers in one nested
interface and the writers in another:

```java
public interface IPv4 {
    interface get {
        static int a(int pack) { return pack >>> 24 & 0xFF; }          // bits 24..31
        static int b(int pack) { return pack >>> 16 & 0xFF; }          // bits 16..23
        static int c(int pack) { return pack >>> 8 & 0xFF; }           // bits 8..15
        static int d(int pack) { return pack & 0xFF; }                 // bits 0..7
    }
    interface set {
        static int a(int pack, int src) { return pack & ~(0xFF << 24) | (src & 0xFF) << 24; }
        static int b(int pack, int src) { return pack & ~(0xFF << 16) | (src & 0xFF) << 16; }
        static int c(int pack, int src) { return pack & ~(0xFF << 8) | (src & 0xFF) << 8; }
        static int d(int pack, int src) { return pack & ~0xFF | src & 0xFF; }
    }
}
```

Call site:

```java
int addr = 0;
addr = IPv4.set.a(addr, 192);
addr = IPv4.set.b(addr, 168);
addr = IPv4.set.c(addr, 1);
addr = IPv4.set.d(addr, 1);
```

Progress — named fields, reviewable bit codecs, a searchable namespace. But the IDE still has nothing useful to work with:

- `addr` is just an `int`. There is no type-level link between `addr` and `IPv4`, so typing `addr.` offers nothing but `Integer`'s inherited static
  methods.
- Passing `addr` to a `void send(int sequenceNumber)` method compiles without complaint — an IP address and a sequence number look identical to the
  compiler.
- Any other `int` in scope can be handed to `IPv4.set.a` as if it were a pack.
- To *discover* `IPv4.get.a`, `IPv4.set.b`, and friends you must remember the class name, type `IPv4.`, and walk the nested-type tree by hand. No
  dot-access on the value itself, no type-narrowed completion, no propagation through assignments.

You have traded one problem (opaque bit masks) for another (correct-by-construction names, but no type distinction from any other `int`). Stock Java
has no syntactic hook that lets an `int` *carry* a type identity like `@IPv4`.

Except…

### What this plugin contributes

The insight is that Java **does** have a type-level marker that attaches to a primitive without changing its representation: a `@Target(TYPE_USE)`
annotation. An `@IPv4 int` is, to the JVM, just an `int` — but to the source, it is a distinct type-use. If the IDE is taught to read that annotation
as "the shape of this pack", every ergonomics problem above dissolves at tool level while the runtime stays as thin as the naive approach.

So, structurally: keep the naive shape — but promote the outer `interface` to an `@interface` and tag every pack-typed slot with it. The accessors
stay gathered in **groups** by what they do, one method per field, the pack first and the value second:

| Group      | Method                                        | Call                                          |
|------------|-----------------------------------------------|-----------------------------------------------|
| `get`      | `static T field( @Pack P pack )`              | `Pack.get.field( pack )`                      |
| `set`      | `static @Pack P field( @Pack P pack, T src )` | `pack = Pack.set.field( pack, value )`        |
| `hasValue` | `static boolean field( @Pack P pack )`        | `Pack.hasValue.field( pack )`                 |
| `to_null`  | `static @Pack P field( @Pack P pack )`        | `pack = Pack.to_null.field( pack )`           |
| `New`      | `static @Pack P of( T field, … )`             | `@Pack P pack = Pack.New.of( 192, 168, 1, 1 )` |

`hasValue` and `to_null` exist for the fields that may hold no value. A nested `@interface Nullable` lifts the whole pack into an optional form. The
names of the groups are not fixed: any nested interface with `static` methods is a group, so `jso` with a method that prints a pack as JSON, or a
group of your own, is offered the same way.

With the plugin installed, the IDE treats that annotation as a distinct, field-addressable type:

- **Dot-access** on a `@FilePerm short` or `@IPv4 int` lists the accessors of every field in the completion popup: `get.a`, `set.a`, `get.b` … Typing
  the name of a field narrows the list to the accessors of that field.
- **Picking a reader** rewrites `pack.` into `Pack.get.field( pack )`.
- **Picking a writer** where a statement stands assigns the result back: `pack = Pack.set.field( pack, <caret> )`.
- **A slot that takes a pack** — an initializer, the right side of an assignment, an argument, a returned value — offers what makes one:
  `Pack.New.of( … )` and the pack-typed constants of the struct, such as `EMPTY_PACK`.
- **Annotation propagation** flows through assignments, casts, returns, and ternaries — `short s = perm;` still shows the `FilePerm` accessors on `s`,
  file-wide.
- **`@<Struct>.Nullable`** wrappers surface a separate completion set that steers you through `hasValue` → `get` before you can unwrap.

You pay the one-time cost of declaring the codec; the IDE picks up the rest. The runtime pays nothing at all.

The same layout exists in TypeScript — see [SlimStruct in TypeScript](#slimstruct-in-typescript). The code generator of the
[AdHoc protocol](https://github.com/AdHoc-Protocol/AdHoc-protocol) emits its value packs in this layout in both languages, so nobody has to write the
codec by hand.

Every example below starts with the stock **C#** form a language with value types would write — then shows the equivalent SlimStruct in **Java**.

### Example 1 — Unix file permissions (9 booleans in a `short`)

The classic `rwx` for owner, group, and other.

**C#:**

```csharp
public struct FilePerm {
    bool ownerRead,  ownerWrite,  ownerExec;
    bool groupRead,  groupWrite,  groupExec;
    bool otherRead,  otherWrite,  otherExec;
}
```

**Java** — nine boolean fields, one bit each, packed into a `short`:

```java
@Target( ElementType.TYPE_USE )
public @interface FilePerm {
	interface get {
		static boolean ownerRead( @FilePerm short pack )  { return ( pack & 1 << 0 ) != 0; }
		
		static boolean ownerWrite( @FilePerm short pack ) { return ( pack & 1 << 1 ) != 0; }
		// ... ownerExec (bit 2), groupRead (3), groupWrite (4),
		//     groupExec (5), otherRead (6), otherWrite (7), otherExec (8)
	}
	
	interface set {
		static @FilePerm short ownerRead( @FilePerm short pack, boolean src ) {
			return ( short ) ( src ?
			                   pack | 1 << 0 :
			                   pack & ~( 1 << 0 ) );
		}
		
		static @FilePerm short ownerWrite( @FilePerm short pack, boolean src ) {
			return ( short ) ( src ?
			                   pack | 1 << 1 :
			                   pack & ~( 1 << 1 ) );
		}
		// ... the other seven
	}
}
```

Call site:

```java
@FilePerm short perm = 0;
perm = FilePerm.set.ownerRead( perm, true );
perm = FilePerm.set.ownerWrite( perm, true );
perm = FilePerm.set.groupRead( perm, true );
perm = FilePerm.set.otherRead( perm, true );

if( FilePerm.get.ownerWrite( perm ) && !FilePerm.get.otherWrite( perm ) ) {
    // owner can modify, others cannot
}
```

A million permission records live in a `short[]` — 2 MB, contiguous, cache-friendly, zero allocation per element.

> Need 64 booleans instead of 9? Keep the same shape, scale up to bit 63, use a `long` as the carrier. Each extra flag costs one bit and two
> methods.

### Example 2 — IPv4 address (4 bytes in an `int`)

**C#:**

```csharp
public struct IPv4 {
    byte a, b, c, d;    // 192.168.1.1 → a=192, b=168, c=1, d=1
}
```

**Java** — four octets packed into a single `int`, most-significant-octet first:

```java
@Target( ElementType.TYPE_USE )
public @interface IPv4 {
	@IPv4 int EMPTY_PACK = 0;
	
	interface get {
		static int a( @IPv4 int pack ) { return pack >>> 24 & 0xFF; }   // bits 24..31
		
		static int b( @IPv4 int pack ) { return pack >>> 16 & 0xFF; }   // bits 16..23
		
		static int c( @IPv4 int pack ) { return pack >>> 8 & 0xFF; }    // bits 8..15
		
		static int d( @IPv4 int pack ) { return pack & 0xFF; }          // bits 0..7
	}
	
	interface set {
		static @IPv4 int a( @IPv4 int pack, int src ) { return pack & ~( 0xFF << 24 ) | ( src & 0xFF ) << 24; }
		
		static @IPv4 int b( @IPv4 int pack, int src ) { return pack & ~( 0xFF << 16 ) | ( src & 0xFF ) << 16; }
		
		static @IPv4 int c( @IPv4 int pack, int src ) { return pack & ~( 0xFF << 8 ) | ( src & 0xFF ) << 8; }
		
		static @IPv4 int d( @IPv4 int pack, int src ) { return pack & ~0xFF | src & 0xFF; }
	}
	
	interface New {
		static @IPv4 int of( int a, int b, int c, int d ) {
			return set.d( set.c( set.b( set.a( EMPTY_PACK, a ), b ), c ), d );
		}
	}
}
```

Type `addr.` on an `@IPv4 int addr` and the popup lists `get.a`, `set.a`, `get.b` … Pick `set.a` and the line becomes

```java
addr = IPv4.set.a( addr, <caret> );
```

Type the value (192) at the caret and you're done.

Call site:

```java
@IPv4 int addr = IPv4.New.of( 192, 168, 1, 1 );
addr = IPv4.set.d( addr, 254 );

System.out.printf( "%d.%d.%d.%d%n",
    IPv4.get.a( addr ), IPv4.get.b( addr ),
    IPv4.get.c( addr ), IPv4.get.d( addr ) );
```

Storing a million addresses costs 4 MB as an `int[]` instead of the ~40 MB a class-based representation would cost, and iteration hits L1 cache the
whole way.

### Example 3 — mixed-width fields (audit event packing a full `long`)

**C#:**

```csharp
public struct AuditEvent {
    long  timestampMs;    // 40 bits — ~34 years of millisecond precision
    byte  kind;           //  8 bits — event type ordinal
    short sequence;       // 16 bits — per-session counter
}
```

**Java** — three differently-sized fields packed edge-to-edge in a single `long`:

```java
@Target( ElementType.TYPE_USE )
public @interface AuditEvent {
	interface get {
		static long timestampMs( @AuditEvent long pack ) { return pack & 0xFF_FFFF_FFFFL; }            // bits 0..39
		
		static int kind( @AuditEvent long pack )         { return ( int ) ( pack >>> 40 & 0xFFL ); }   // bits 40..47
		
		static int sequence( @AuditEvent long pack )     { return ( int ) ( pack >>> 48 & 0xFFFFL ); } // bits 48..63
	}
	
	interface set {
		static @AuditEvent long timestampMs( @AuditEvent long pack, long src ) {
			return pack & ~0xFF_FFFF_FFFFL | src & 0xFF_FFFF_FFFFL;
		}
		
		static @AuditEvent long kind( @AuditEvent long pack, int src ) {
			return pack & ~( 0xFFL << 40 ) | ( src & 0xFFL ) << 40;
		}
		
		static @AuditEvent long sequence( @AuditEvent long pack, int src ) {
			return pack & ~( 0xFFFFL << 48 ) | ( src & 0xFFFFL ) << 48;
		}
	}
}
```

Billions of events fit in a `long[]`. Three bit-ops retrieve any field.

### Example 4 — per-field nullability (a 3-state boolean)

A Boolean with a third "undecided" state needs 2 bits, not 1. Real-world cases: a cookie-consent flag that defaults to "ask", a feature toggle that a
user hasn't touched yet, a survey answer that can be skipped.

**C#:**

```csharp
public struct UserChoice {
    bool? subscribed;       // null / true / false — 2 bits
    bool  acknowledged;     // true / false        — 1 bit
}
```

The nullable boolean uses three of the four available 2-bit states:

| bits   | meaning  |
|--------|----------|
| `0b00` | `false`  |
| `0b01` | `true`   |
| `0b10` | `null`   |
| `0b11` | *unused* |

**Java** — the 2-bit field appears in two more groups, `hasValue` and `to_null`:

```java
@Target( ElementType.TYPE_USE )
public @interface UserChoice {
	interface get {
		static boolean subscribed( @UserChoice byte pack )   { return ( pack & 0x3 ) == 0x1; }   // bits 0..1 — 0=false, 1=true, 2=null
		
		static boolean acknowledged( @UserChoice byte pack ) { return ( pack & 0x4 ) != 0; }     // bit 2
	}
	
	interface set {
		static @UserChoice byte subscribed( @UserChoice byte pack, boolean src ) {
			return ( byte ) ( pack & ~0x3 | ( src ?
			                                   0x1 :
			                                   0x0 ) );
		}
		
		static @UserChoice byte acknowledged( @UserChoice byte pack, boolean src ) {
			return ( byte ) ( src ?
			                  pack | 0x4 :
			                  pack & ~0x4 );
		}
	}
	
	interface hasValue {
		static boolean subscribed( @UserChoice byte pack ) { return ( pack & 0x3 ) != 0x2; }
	}
	
	interface to_null {
		static @UserChoice byte subscribed( @UserChoice byte pack ) { return ( byte ) ( pack & ~0x3 | 0x2 ); }
	}
}
```

Call site — start from zero (both fields `false`), or immediately push `subscribed` to `null` with `to_null`:

```java
@UserChoice byte choice = UserChoice.to_null.subscribed( ( byte ) 0 );

if( !UserChoice.hasValue.subscribed( choice ) ) {
    choice = UserChoice.set.subscribed( choice, askUser() );
}
choice = UserChoice.set.acknowledged( choice, true );

if( UserChoice.hasValue.subscribed( choice ) &&
    UserChoice.get.subscribed( choice ) ) {
    sendNewsletter();
}
```

Typing `choice.` and then `subscribed` leaves the four accessors of the nullable field in the popup — `get.subscribed`, `set.subscribed`,
`hasValue.subscribed`, `to_null.subscribed` — so `hasValue` is in front of you when you reach for `get`.

### Example 5 — whole-pack `Nullable` wrapper (an optional IPv4)

Sometimes the pack itself may be absent: a config entry that might not be set, a DNS lookup that failed, a slot in a cache.

**C#:**

```csharp
public struct MaybeAddress {
    IPv4? value;      // an IPv4 address, or nothing
}
```

**Java** — nest a `@Nullable` annotation inside `IPv4`, in a carrier one size wider so that the sentinel is a value no address can take:

```java
@Target( ElementType.TYPE_USE )
public @interface IPv4 {
	// ... EMPTY_PACK and the groups shown above ...
	
	@Target( ElementType.TYPE_USE )
	@interface Nullable {
		interface value {
			static boolean hasValue( @Nullable long src ) { return src != NULL; }
			
			static @IPv4 int get( @Nullable long src )    { return ( int ) src; }
			
			static @Nullable long set( @IPv4 int src )    { return src & 0xFFFF_FFFFL; }
			
			static @Nullable long to_null()               { return NULL; }
		}
		
		@Nullable long NULL = 0x1_0000_0000L;          // sentinel: above every address
	}
}
```

Usage:

```java
@IPv4.Nullable long maybe = IPv4.Nullable.value.to_null();

if( IPv4.Nullable.value.hasValue( maybe ) ) {
    @IPv4 int addr = IPv4.Nullable.value.get( maybe );
    // use addr ...
}

@IPv4 int home = IPv4.New.of( 127, 0, 0, 1 );
@IPv4.Nullable long lifted = IPv4.Nullable.value.set( home );
```

`@IPv4` and `@IPv4.Nullable` are distinct types to the plugin. On an `@IPv4 int`, dot-completion offers the accessors of every octet **plus** the
`set` of `Nullable` that lifts the pack. On an `@IPv4.Nullable long`, dot-completion offers only what takes the wrapper — `value.hasValue`,
`value.get` — steering you through the correct unwrap sequence.

### SlimStruct in TypeScript

TypeScript has the same gap. A `number` is the only thing that costs nothing, an object per record costs a heap allocation, and
`type IPv4 = number` is an alias — a second name of `number`, not a type of its own. The layout is the one of Java, with a namespace where Java has
an interface:

```typescript
export type IPv4 = number;

export namespace IPv4 {
    export namespace get {
        export function a(pack: IPv4): number { return pack >>> 24 & 0xFF }
        export function d(pack: IPv4): number { return pack & 0xFF }
        // ... b, c
    }
    export namespace set {
        export function a(pack: IPv4, src: number): IPv4 { return pack & ~(0xFF << 24) | (src & 0xFF) << 24 }
        export function d(pack: IPv4, src: number): IPv4 { return pack & ~0xFF | src & 0xFF }
        // ... b, c
    }
    export namespace New {
        export function of(a: number, b: number, c: number, d: number): IPv4 {
            return set.d(set.c(set.b(set.a(EMPTY_PACK, a), b), c), d)
        }
    }

    export const EMPTY_PACK = <IPv4>0;

    export type Nullable = IPv4 | Nullable.NULL;
    export namespace Nullable {
        export const NULL = 0x1_0000_0000;
        export type  NULL = 0x1_0000_0000;

        export function hasValue(pack: Nullable): boolean { return pack !== NULL }
        export function get(src: Nullable): IPv4 { return <IPv4>src }
        export function set(src: IPv4): Nullable { return <Nullable>src }
        export function to_null() { return NULL }
    }
}
```

Call site:

```typescript
let addr: IPv4 = IPv4.New.of(192, 168, 1, 1);
addr = IPv4.set.d(addr, 254);
console.log(IPv4.get.a(addr), IPv4.get.d(addr));
```

What the plugin does is what it does in Java: `addr.` lists `get.a`, `set.a` …, a reader becomes `IPv4.get.a(addr)`, a writer where a statement
stands becomes `addr = IPv4.set.a(addr, <caret>)`, a slot that takes a pack offers `New.of` and `EMPTY_PACK`.

A number knows nothing of the name it was declared under, so the plugin reads the name from the source, the way the Java side reads the annotation:

- the declared type of a variable, a parameter, a field, an item of an array — `let addr: IPv4`, `all: IPv4[]`, `addr: IPv4 | undefined`;
- the declared return type of the function a value comes from — `let addr = IPv4.New.of(1, 2, 3, 4)`;
- a cast — `<IPv4>x`, `x as IPv4`;
- what is assigned to the variable later in its function.

The call is written the way the file already reaches the pack. With `const C = Protocol.Testers.Command` and `let request = C.New.of(…)`, picking a
reader on `request.` writes `C.get.kind(request)`.

A pack wider than a `number` can hold exactly is a class with the fields as properties, and the namespace of the same name holds the same groups.
There the plugin adds its items to what TypeScript offers for the object and suppresses nothing.

TypeScript support needs the *JavaScript and TypeScript* plugin of JetBrains — bundled in IntelliJ IDEA, WebStorm, and the other IDEs that work
with TypeScript. Without it the Java side works alone.

### The former layout — a field first

Earlier versions of the plugin described a layout where every field was a nested `@interface` of its own, holding an interface `Val` with the
accessors, the value first and the pack second:

```java
@Target( ElementType.TYPE_USE )
public @interface IPv4 {
	@Target( ElementType.TYPE_USE )
	@interface a {                          // bits 24..31
		interface Val {
			static int get( @IPv4 int src ) { return ( src >>> 24 ) & 0xFF; }
			
			static @IPv4 int set( int v, @IPv4 int dst ) {
				return dst & ~( 0xFF << 24 ) | ( v & 0xFF ) << 24;
			}
		}
	}
	// ... b, c, d
}
```

The plugin still understands it, a struct may even mix the two. The popup shows the short names of the methods with the field in the type column:

![img.png](img.png)

after completion

![img_1.png](img_1.png)

at the caret position type the value (192) and you're done.

![img_2.png](img_2.png)

New code is better written in groups: a call reads as what it does to which field — `IPv4.set.a( addr, 192 )` — and the pack always stands first.

---

## What the plugin contributes

### 1. Dot-access offers every field accessor

Type `perm.` on a `@FilePerm short perm` and the popup lists every static method of every group: `get.ownerRead`, `set.ownerRead`,
`get.ownerWrite`, and so on. On an `@IPv4 int`, you see `get.a`, `set.a`, `New.of`, etc. The label is the group and the method, the type column
shows what the method returns, the tail text shows the parameter signature. The name of the field alone is enough to find its accessors.

Picking `set.a` where a statement stands rewrites the line to

```java
addr = IPv4.set.a( addr, <caret> );
```

with the caret in the value slot. `JavaCodeStyleManager.shortenClassReferences` collapses the `IPv4.` prefix against existing imports.

### 2. A slot that takes a pack offers what makes one

At `@IPv4 int addr = <caret>`, at the right side of an assignment, at an argument or a returned value of the pack's type, the popup offers the
methods of the groups that return a pack and take none — `New.of` — and the pack-typed constants of the struct — `EMPTY_PACK`. They are added to
what Java offers at that place, nothing is suppressed.

### 3. Annotation propagation across assignments

Declare `int x = addr;` — the plugin remembers `x` is effectively `@IPv4 int`. Dot-access on `x` offers the same accessors as `addr`. This propagates
transitively through:

- initializers and reassignments of local variables;
- method returns (if the method's return type is annotated);
- casts (`(int) x`) and parenthesised expressions;
- ternary operands (`cond ? a : b`);
- field write sites within the same file.

Analysis uses `ReferencesSearch` over the enclosing method (for locals) or file (for fields), so it stays fast enough to run on every keystroke.

### 4. Nullable-wrapper completions adapt to the exact annotation

- On `@IPv4 int addr` — offers the field accessors and `IPv4.Nullable.value.set( addr )` (lift).
- On `@IPv4.Nullable long maybe` — offers only `IPv4.Nullable.value.hasValue( maybe )` and `.get( maybe )`.

The plugin distinguishes them by the exact annotation class on each method's parameter, not by a name heuristic. Both views never leak into each
other.

### 5. Cross-struct value slots compose with SlimEnum

When a setter's value parameter is itself a SlimEnum-annotated type (e.g. `kind( @AuditEvent long pack, @EventKind int src )`), the plugin inserts
`pack = AuditEvent.set.kind( pack, <caret> )` with the caret at the value. SlimEnum then offers the valid `@EventKind` constants at that caret
position. The two features compose transparently.

### 6. Default completion is suppressed only where nothing else fits

The contributor registers with `order="first"` and calls `result.stopHere()` only when it has emitted items, and only at two kinds of places:

- dot-access on a pack that is a primitive — what Java offers for an `int`, or TypeScript for a `number`, is of no use there;
- a slot of a SlimEnum — its constants are all that fits.

A slot that takes a pack keeps what the language offers: a pack comes from a variable or a call as often as from `New.of`. So does a TypeScript
pack that is an object of a class: its fields stay in the popup. Anywhere else the plugin stays silent, and default completion runs untouched.

---

## The philosophy

**Java the language, unmodified, cannot express any of this.** No `struct`, no opaque types, no value classes with a guaranteed-unboxed
representation, no user-defined primitives. Project Valhalla will eventually deliver something resembling this, but not on your current LTS.

SlimEnum + SlimStruct gives you the ergonomics of named, typed, field-addressable value aggregates **today**:

- **Zero runtime cost.** Bytecode is literally primitives. No wrapping, no boxing under arrays or generics, no hidden allocations. Matches Scala 3
  opaque types' guarantee — not Scala 2 value classes' "sometimes boxes" guarantee.
- **Tool-side only.** Nothing for downstream consumers of your API to install; they see plain Java annotations they can safely ignore.
- **Works with any Java version.** No preview flags, no compiler plugins, no agent, no bytecode rewriting.
- **Composes with existing Java idioms.** Packs are primitives — they fit in `int[]`, `long[]`, `Map<Long, Long>`,
  `ConcurrentHashMap.computeIfAbsent`, varargs, anywhere you already use primitives.

You are not extending Java the language. You are extending Java **the IDE experience** — which, for most real-world development, is the part that
decides whether a pattern is actually usable.

---

## Installation

**From the JetBrains Marketplace**

1. Open `Settings / Preferences` → `Plugins`.
2. Go to the `Marketplace` tab.
3. Search for **SlimEnum** and click `Install`.
4. Restart the IDE if prompted.

**From a local ZIP**

1. Download the [latest release](https://github.com/cheblin/SlimEnum/releases/latest).
2. Open `Settings / Preferences` → `Plugins`.
3. Click the gear icon → `Install plugin from disk…`.
4. Select the downloaded ZIP and restart when prompted.
