all:
	@echo "Possible actions are:";\
	 echo "	make world";\
	 echo "	make javadoc";\
	 echo "	make run";\
	 echo "	make test";\
	 echo "	make testv  -- verbose test";\
	 echo "	make testvv -- even more verbose test";\
	 echo "	make clean";

.PHONY: clean world test javadoc

include SETTINGS

world:
	mkdir -p classes/ && \
	cd src/ && $(MAKE) && \
	cd ../ && $(JAR) cf field2sql_parser.jar -C classes/ stanford/

javadoc:
	mkdir -p javadoc/ && $(JAVADOC) -d javadoc/ src/Field2SQL.java src/PostFilter.java src/FieldPostFilter.java src/NetDBPostFilter.java

run:
	$(JAVA) -cp field2sql_parser.jar:$(ANTLR_JAR) stanford.netdb.utils.Field2SQL

test:
	$(JAVA) -cp field2sql_parser.jar:$(ANTLR_JAR) stanford.netdb.utils.Field2SQL test

testv:
	$(JAVA) -cp field2sql_parser.jar:$(ANTLR_JAR) stanford.netdb.utils.Field2SQL test -v

testvv:
	$(JAVA) -cp field2sql_parser.jar:$(ANTLR_JAR) stanford.netdb.utils.Field2SQL test -vv

clean:
	rm -f field2sql_parser.jar *~;\
	rm -rf javadoc classes;\
	cd src/ && $(MAKE) clean ;\
