# jarbloat

A simple tool to analyze the size of a Java JAR file. Inspired with [go-size-analyzer](https://github.com/Zxilly/go-size-analyzer).

## Usage

```
$ java -jar jarbloat.jar [options] <app.jar>
```

## Compilation

To compile jarbloat, run:

```
make uberjar
```

and you will get standalone uberjar in `target/jarbloat.jar`. To
compile it to standalone binary, install
[GraalVM](https://www.graalvm.org/) and run:

```
make binary NATIVE_IMAGE=<path/to/native-image>
```

## License

Copyright © 2024 Sanel Zukan

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
