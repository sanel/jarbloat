# jarbloat
[![jarbloat tests](https://github.com/sanel/jarbloat/actions/workflows/clojure.yml/badge.svg)](https://github.com/sanel/jarbloat/actions)

A simple tool to analyze the content and size of a Java JAR file. Inspired with [go-size-analyzer](https://github.com/Zxilly/go-size-analyzer).

Features:
 * Works on Java >= 8
 * Detail size breakdown by files and packages
 * Support multiple output formats: table (can be used in markdown or
   [org-mode](https://orgmode.org/) files), json, csv and interactive html
 * Support for analyzing the dependency of each class file (within a jar) and
   displaying dependency graph in [Graphviz dot](https://graphviz.org/) format
 * It can be compiled with [GraalVM](https://www.graalvm.org/) for
   fast startup (for now tested only on Linux)

## Usage

```
$ java -jar jarbloat.jar [options] <app.jar>
```

## Compilation

To compile jarbloat, make sure you have
[leiningen](https://leiningen.org/) installed and `make`. Run:

```
make uberjar
```

and you will get a standalone uberjar in `target/jarbloat.jar.` To
compile it to a standalone binary, install
[GraalVM](https://www.graalvm.org/) and run:

```
make native NATIVE_IMAGE=<path/to/native-image>
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
