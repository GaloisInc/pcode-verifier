run: build
	java -cp . com.galois.symbolicSimulator.PCodeParser fib.xml

build:
	javac -cp . -d . com/galois/symbolicSimulator/PCode*.java com/galois/symbolicSimulator/Varnode.java

test:
	echo "JUnit is probably best built with Eclipse"

clean:
	rm com/galois/symbolicSimulator/*.class

