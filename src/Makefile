all: parser

.PHONY: parser run clean

clean:
	rm -f *~ *.class *.txt StringsParser.java FieldTreeParser.java \
		StringsLexer.java StringsParserTokenTypes.java

parser:
	$(ANTLR) field.g && \
	$(JAVAC) -classpath $(ANTLR_JAR):../classes/ -d ../classes/ *.java

run:
	$(JAVA) -cp $(ANTLR_JAR):../classes/ Main
