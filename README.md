# SlimEnum

<!-- Plugin description -->
Well known, that in some use cases Java enums are [not so good](https://youtu.be/Hzs6OBcvNQE) as they
could be.

The drawback of Java enums is especially obvious when they are heavy used in high loaded systems, whenever performance and memory consumptions are crucial.

To overcome this problem developers use simple primitive type constants instead of enums.

The advantage of this solution is its lightweight, natural, fit to build as
simple enums, as flags.

But this has some limitation: no compile-time check and no IDEA code completion support

The root of the problem is that the Java type system does not support the building of new types by extending primitives.

This plugin is utilized java annotation construction, to declare the named
constants set and bind it to primitive type variables, fields, methods
arguments, and return type.

With this plugin, IDE getting smart code completion capability.

SlimEnum is not a total replacement for Java language embedded enums, it is an addition in the case when performance is a concern and primitive constants can be used.
<!-- Plugin description end -->
Step-by-step guide

Declare **SlimEnum**
```java
@interface Font { //declare Font annotation with constant fields
	
	byte    NORMAL    = 0, //constants set for variables of byte type
                BOLD      = 1,
                UNDERLINE = 3,
                BLINK     = 4,
                INVERSE   = 5,
                STRIKE    = 6;
	
	String  Helvetica  = "Helvetica", //constants set if variable is String
                Palatino   = "Palatino",
                HonMincho  = "HonMincho",
                Serif      = "Serif",
                Monospaced = "Monospaced",
                Dialog     = "Dialog";
	
	@interface Foreground { // enclosed declarations is ok
		int     BLACK   = 0, // if the first and
                        RED     = 1, // second constant names follow in alphabetic order this is just enum
                        GREEN   = 2, // a constant value for field can be used only once, values combinations have no sense
                        YELLOW  = 3,
                        BLUE    = 4,
                        MAGENTA = 5,
                        CYAN    = 6,
                        WHITE   = 7,
                        DEFAULT = 8;
	}
	
	@interface Background {
		int     RED         = 1, // if the first and
                        BLACK       = 0, // second constant names follow in descending alphabetical order means this is a flag
                        GREEN       = 2, // a constant value combination for field is ok
                        YELLOW      = 3,
                        BLUE        = 4,
                        MAGENTA     = 5,
                        CYAN        = 6,
                        WHITE       = 7,
                        DEFAULT     = 8,
                        TRANSPARENT = 0;
	}
}
```
apply to variables, fields, methods arguments and return type
```java
class Test {
	@Font String name;
	@Font.Foreground int fg;
	
	@Font.Background int setBackground( @Font.Background int bg ) { return bg; }
	
	static void createFont( @Font String name, @Font byte style, @Font.Background int background, @Font.Foreground int foregraund ) { }
}
```
...and then use it
```java
public class Main {
	
	public static void main( String[] args ) {
		Test.createFont( Font.Monospaced + Font.HonMincho, Font.BLINK, Font.Background.CYAN, Font.Foreground.BLACK );
		@Font String         name = Font.Helvetica;
		@Font byte           type = Font.NORMAL | Font.BOLD | Font.INVERSE;
		@Font.Foreground int fg   = Font.Foreground.BLUE;
		
		Test test = new Test();
		test.setBackground( Font.Background.BLUE );
		test.fg = Font.Foreground.BLUE | Font.Foreground.CYAN | Font.Foreground.MAGENTA;
		
		test.fg = Font.Foreground.BLUE;
		
		if (type == (Font.NORMAL | Font.BOLD | Font.INVERSE | Font.BLINK | Font.STRIKE) && test.setBackground( Font.Background.BLUE ) == Font.Background.RED)
		{
			@Font.Foreground int fgw = Font.Foreground.CYAN;
		}
		assert (test.setBackground( Font.Background.BLUE ) == (Font.Background.CYAN | Font.Background.DEFAULT));
		
		if (test.fg == Font.NORMAL && test.fg != Font.NORMAL)
		{
		}
		
		@Font String fontName = Font.Dialog;
		
		switch (test.setBackground( Font.Background.BLUE ))
		{
			case Font.Background.BLUE:
				break;
			case Font.Background.CYAN:
				break;
		}
	}
}
```
![1](https://user-images.githubusercontent.com/29354319/106711963-4613cc80-6633-11eb-8f80-990b67ed1b00.PNG)

![3](https://user-images.githubusercontent.com/29354319/106712528-0bf6fa80-6634-11eb-9878-91dac4e5bc4d.PNG)

![4](https://user-images.githubusercontent.com/29354319/106712318-be7a8d80-6633-11eb-9f83-23f93779b969.PNG)

![5](https://user-images.githubusercontent.com/29354319/106712373-d05c3080-6633-11eb-9f7a-9c8df51985b7.PNG)

![6](https://user-images.githubusercontent.com/29354319/106712403-dd791f80-6633-11eb-91f3-107b93085024.PNG)

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "SlimEnum"</kbd> >
  <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/cheblin/SlimEnum/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
