/************************************************************************
 *
 * Field2SQL.java -
 *
 * $Id: Field2SQL.java,v 1.13 2008/07/29 21:22:37 priimak Exp $
 *
 *-----------------------------------------------------------------------
 * Copyright (c) 2005, Board of Trustees, Leland Stanford Jr. University
 ************************************************************************/
package stanford.netdb.utils;

import java.io.*;
import java.util.regex.*;
import antlr.collections.*;
import antlr.debug.misc.*;
import antlr.*;

/**
 * SQLizer is an utility library intented to be used in search applications. Given an input
 * string it builds SQL select statement. It supports two types of inputs, numeric and string.
 * The catch is that this library allows usage of operators in the input string. Supported
 * operators are 'and', 'or' and 'not'. It also supports wildcards '*', '%', '?' and '_' in
 * string searches. In case of numeric searches no wildcards are supported. To accommodate
 * searches by strings, which have wildcards in them, backslashes '\' can be used to escape
 * appropriate characters. Obviously to search for '\' it needs to be escaped as well. Strings
 * with spaces needs to be enclosed in double quotes. The other possibility is to do a regex
 * search, which is accomplished by enclosing string in forward slashes. The resulting SQL is
 * targeted toward Oracle regex expressions. For example, following input strings will be
 * appropriately evaluated.<br><br>
 *
 *  a or b<br><br>
 *  &nbsp;&nbsp;&nbsp;&nbsp;( ( select id from Record where name = 'a' ) UNION ( select id from Record where name = 'b' ) )<br><br>
 *
 *  not a and not b<br><br>
 *  &nbsp;&nbsp;&nbsp;&nbsp;( ( select id from Record MINUS ( select id from Record where name = 'a' ) ) INTERSECT ( select id from Record MINUS ( select id from Record where name = 'b' ) ) )<br><br>
 *
 *  "a b c" or a\\*<br><br>
 *  &nbsp;&nbsp;&nbsp;&nbsp;( ( select id from Record where name = 'a b c' ) UNION ( select id from Record where name LIKE 'a\\%' ESCAPE '\' ) )<br><br>
 *
 *  /^.*[[:digit:]]$/i<br><br>
 *  &nbsp;&nbsp;&nbsp;&nbsp;( select id from Record where REGEXP_LIKE(name, '^.*[[:digit:]]$', 'i') )<br><br>
 *
 * We use unions and intersects in order to accomodate searches for objects with multi-value attributes.
 */
public class Field2SQL {

    /**
     * Generated SQL assumes that column type is numeric.
     */
    public static int NUMERIC     = 0;

    /**
     * Generated SQL assumes that column type is string.
     */
    public static int STRING      = 1;

    /**
     * This match is Stanford NetDB specific. Generated SQL assumes that column
     * type is string and few additional transformations are applied.
     */
    public static int DOMAIN_NAME = 2;

    private String field_display_name;    // used in error messages
    private String table;                 // table name
    private String column;                // column name
    private String str;                   // string to parse
    private int type;
    private Object          post_filter;  // post filter
    private FieldPostFilter expr_builder; // expression builder

    // type = {NUMERIC, STRING, DOMAIN}
    private Field2SQL(int type,                    String table,
                      String column,               String str,
                      String field_display_name,   Object post_filter,
                      FieldPostFilter expr_builder)
    {
        this.type               = type;
        this.table              = table;
        this.column             = column;
        this.str                = escape(str)+";";
        this.field_display_name = field_display_name;
        this.post_filter        = post_filter;
        this.expr_builder       = expr_builder;
    }

    /**
     * Please do not use this function.
     */
    protected static String escape(String str) {
        return str.replaceAll("&", "&amp").replaceAll(";", "&semicolon").
            replaceAll("\\\\\\\\", "&dbl").replaceAll("\\\\/", "&v");
    }

    /**
     * Please do not use this function.
     */
    protected static String unescape(String str) {
        return unescape(str, false);
    }

    /**
     * Please do not use this function.
     */
    protected static String unescape(String str, boolean vs_intact) {
        return str.replaceAll("&v", (vs_intact?"\\\\/":"/")).replaceAll("&dbl", "\\\\\\\\").
            replaceAll("&semicolon", ";").replaceAll("&amp", "&");
    }

    /**
     * Uses FieldPostFilter as an expression builder to build sql
     *
     * @param type                one of NUMERIC, STRING, DOMAIN_NAME
     * @param table               name of the table to be figured in reaulting SQL expression
     * @param column              column to be used in '=' or 'LIKE' expression
     * @param str                 string to parse
     * @param field_display_name  parameter which is used only in error messages if given
     * @param expr_builder        once the parsing is complete and each field value separated
     *                            from source string, expr_builder.filter(...) method is called
     *                            and its result is used as a 'where' expression, e.g.
     *                            select id from table where `expr_builder.filter(table, column, field)`
     *                            @see stanford.netdb.utils.FieldPostFilter#filter(String table, String column, String field)
     */
    public static String buildField(int type,   String table, String column,
                                    String str, String field_display_name,
                                    FieldPostFilter expr_builder)
        throws Exception
    {
        return (new Field2SQL(type, table, column, str, field_display_name,
                              new FieldPostFilter(), expr_builder)).doParse();
    }

    /**
     * @param type                one of NUMERIC, STRING, DOMAIN_NAME
     * @param table               name of the table to be figured in reaulting SQL expression
     * @param column              column to be used in '=' or 'LIKE' expression
     * @param str                 string to parse
     * @param field_display_name  parameter which is used only in error messages if given
     * @param post_filter         once the parsing is complete and each field value separated
     *                            from source string, post_filter.filter(...) method is called
     *                            and its result is used as a <strong>final field value</strong>.
     *                            This variable must be either instance of FieldPostFilter or PostFilter
     *                            @see stanford.netdb.utils.FieldPostFilter#filter(String table, String column, String field)
     *                            @see stanford.netdb.utils.PostFilter
     */
    public static String parseField(int type,   String table, String column,
                                    String str, String field_display_name,
                                    Object post_filter)
        throws Exception
    {
        if( !(post_filter instanceof FieldPostFilter) &&
            !(post_filter instanceof PostFilter) )
            throw new Exception("post_filter must be instance of "+
                                "either PostFilter or FieldPostFilter.");
        return (new Field2SQL(type, table, column, str, field_display_name, post_filter, null)).doParse();
    }

    /**
     * Same as {@link #parseField(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Object) parseField(...)}
     * with post_filter set to the default one, which performs no transormations on final field values
     */
    public static String parseField(int type,   String table, String column,
                                    String str, String field_display_name)
        throws Exception
    {
        return (new Field2SQL(type, table, column, str, field_display_name,
                              new FieldPostFilter(), null)).doParse();
    }

    /**
     * Same as {@link #parseField(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String) parseField(...)}
     * with type = STRING
     */
    public static String parseStringField(String table, String column,
                                          String str,   String field_display_name)
        throws Exception
    {
        return (new Field2SQL(STRING, table, column, str, field_display_name,
                              new FieldPostFilter(), null)).doParse();
    }

    /**
     * Same as {@link #parseField(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String) parseField(...)}
     * with type = INT
     */
    public static String parseIntField(String table, String column,
                                       String str,   String field_display_name)
        throws Exception
    {
        return (new Field2SQL(NUMERIC, table, column, str, field_display_name,
                              new FieldPostFilter(), null)).doParse();
    }

    private String doParse() throws Exception {
        try {
            // first generate AST
            StringsLexer lexer = new StringsLexer(new StringReader(str));
            if( type == NUMERIC )
                lexer.match_numbers = true;

            StringsParser parser = new StringsParser(lexer);
            parser.expr();
            CommonAST ast = (CommonAST)parser.getAST();

            // now walk down the AST and generate sql
            FieldTreeParser stp = new FieldTreeParser();

            stp.table              = table;
            stp.column             = column;
            stp.post_filter        = post_filter;
            stp.expression_builder = expr_builder;

            if(      type == NUMERIC )
                stp.match_numbers = true;

            else if( type == DOMAIN_NAME )
                stp.domain_name   = true;

            return stp.expr(ast);

        } catch (Exception ex){
            str = unescape(str, true);
            String msg = ex.getMessage();
            if( expr_builder != null && msg != null && msg.trim().length() > 0 ) {
                throw new Exception("In \""+field_display_name+"\" field "+msg.trim());
            } else {
                if( field_display_name != null )
                    throw new Exception("\""+field_display_name+"\" field \""+str.substring(0, str.length()-1)+
                                        "\" is not a valid "+(type==NUMERIC?"numeric":"string")+" field.");
                else
                    throw new Exception("Invalid "+(type==NUMERIC?"numeric":"string")+
                                        " field \""+str.substring(0, str.length()-1)+"\".");
            }
        }
    } // end of doParse()

    /**
     * SQLizer shell. Run this to bring up sqlizer shell, where you can try different
     * input parameters and see generated SQL.
     */
    public static void main(String[] args) {
        String column = "name";
        String table = "Record";
        boolean netdb_filter = false;
        try {
            if( args.length == 0 ) {
                // read from stdin
                BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
                String line;
                int ptype = STRING;
                System.out.print("NetDB Field Parser. Type 'help' for list of commands.\nCurrent settings are:\n");
                show_settings(ptype, netdb_filter, table, column);
                System.out.print(">>> ");
                while( ( line = stdin.readLine() ) != null ){
                    line = line.trim();

                    if( line.equals("exit") || line.equals("quit") )
                        return;

                    else if( line.equals("help") ) {
                        help(ptype, netdb_filter, table, column);

                    } else if( line.equals("netdb_filter") ) {
                        if( netdb_filter ) {
                            netdb_filter = false;
                            System.out.println("Stanford NetDB specific filter is inactive.\n");
                        } else {
                            netdb_filter = true;
                            System.out.println("Stanford NetDB specific filter is active.\n");
                        }
                        show_settings(ptype, netdb_filter, table, column);

                    } else if( line.equals("numeric") ) {
                        ptype = NUMERIC;
                        System.out.println("Type set to numeric.\n");
                        show_settings(ptype, netdb_filter, table, column);

                    } else if( line.equals("domain") ) {
                        ptype = DOMAIN_NAME;
                        netdb_filter = true;
                        System.out.println("Type set to domain.\n");
                        show_settings(ptype, netdb_filter, table, column);

                    } else if( line.equals("string") ) {
                        ptype = STRING;
                        System.out.println("Type set to string.\n");
                        show_settings(ptype, netdb_filter, table, column);

                    } else if( line.matches("column\\s*=\\s*\\S+\\s*$") ) {
                        column = line.substring(line.indexOf("=")+1).trim();
                        System.out.println("Column name set to \""+column+"\".\n");
                        show_settings(ptype, netdb_filter, table, column);

                    } else if( line.matches("table\\s*=\\s*\\S+\\s*$") ) {
                        table = line.substring(line.indexOf("=")+1).trim();
                        System.out.println("Table name set to \""+table+"\".\n");
                        show_settings(ptype, netdb_filter, table, column);

                    } else {
                        try {
                            if( ptype == STRING || ptype == DOMAIN_NAME ) {
                                if( netdb_filter )
                                    System.out.println(parseField(ptype, table, column, line, null,
                                                                  new NetDBPostFilter()));
                                else
                                    System.out.println(parseStringField(table, column, line, null));
                            } else
                                System.out.println(parseIntField(table, column, line, null));
                        } catch (Exception ex) {
                            System.out.println("ERROR: "+ex.getMessage());
                        }
                    }
                    System.out.print(">>> ");
                }

            } else if( args[0].equals("test") ) {
                // do regression test
                System.out.println("Regression test for Field2SQL parser.");
                int verbose = 0;
                try {
                    if( args[1].equals("-v") )
                        verbose = 1;
                    else if( args[1].equals("-vv") )
                        verbose = 2;
                } catch (Exception ex) { }
                BufferedReader file = new BufferedReader(new FileReader("src/Field2SQL.java"));
                String line;
                boolean do_test = false;
                int counter = 1;
                while( ( line = file.readLine() ) != null ){
                    line = line.trim();
                    //System.out.println(line);
                    if( line.equals("BEGIN REGRESSION TEST DATA") ) {
                        do_test = true;
                        continue;
                    }

                    if( !do_test || line.equals("") )
                        continue;

                    if( line.equals("END REGRESSION TEST DATA") )
                        return;

                    // parse the line and extract test case and expected result
                    Matcher m = Pattern.compile("^(\\S+)\\s+;(.*);.*;(.*);.*$").matcher(line);
                    if( !m.matches() || m.groupCount() != 3 ) {
                        System.out.println("ERROR: Invalid test case ["+line+"]");
                        continue;
                    }

                    String type = m.group(1);
                    String test_case = m.group(2);
                    String expected = m.group(3);

                    if( !type.equals("COLUMN") ) {
                        if( verbose > 0 )
                            System.out.print(pad("\""+test_case+"\"", 75)+" ");
                        else
                            System.out.print(pad(""+(counter++), 5));
                    }

                    String result = "";
                    try {
                        if( type.equals("COLUMN") ) {
                            column = test_case;

                        } else if( type.equals("STRING") ) {
                            result = parseStringField(table, column, test_case, null).trim();

                        } else if( type.equals("NETDB") ) {
                            result = parseField(STRING, table, column, test_case, null,
                                                new NetDBPostFilter()).trim();

                        } else if( type.equals("NUMERIC") ) {
                            result = parseIntField(table, column, test_case, null).trim();

                        } else
                            System.out.println("ERROR: Invalid test type ["+type+"]");

                        if( !type.equals("COLUMN") ) {
                            if( expected.equals("ERROR") )
                                System.out.println("Failed. Expected ERROR.");
                            else if( expected.equals(result) ) {
                                System.out.println("Ok.");
                                if( verbose == 2 )
                                    System.out.println("\tResult \""+result+"\"\n");
                            } else {
                                System.out.println("Failed.\nTest ["+test_case+"]\nExpected ["+expected+"].\nFound    ["+result+"]\n");
                                if( verbose == 2 )
                                    System.out.println("\tExpected \""+result+"\"");
                                if( verbose == 1 )
                                    System.out.println("\tGot      \""+result+"\"");
                                if( verbose == 2 )
                                    System.out.println("");
                            }
                        }
                    } catch (Exception ex) {
                        if( expected.equals("ERROR") ) {
                            System.out.println("Ok.");
                            if( verbose == 2 )
                                System.out.println("\tResult \"ERROR\"\n");
                        } else
                            System.out.println("Failed. Expected success.");
                    }
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    } // end of main(...)

    private static String pad(String str, int min_length) {
        if( str.length() >= min_length )
            return str;

        int extra_length = min_length - str.length();
        char[] extra = new char[extra_length];
        for( int i = 0; i < extra_length; i++ )
            extra[i] = ' ';

        return str.concat(new String(extra));
    } // end of format(...)

    private static void help(int ptype, boolean netdb_filter, String table, String column) {
        help(ptype, netdb_filter, table, column, false);
    }

    private static void show_settings(int ptype, boolean netdb_filter, String table, String column) {
        help(ptype, netdb_filter, table, column, true);
    }

    private static void help(int ptype, boolean netdb_filter, String table, String column, boolean settings_only) {
        System.
            out.
            println((settings_only?"":"Commands:\nhelp               - to see this help.\n")+
                    "netdb_filter"+(netdb_filter      ?" (on)":"     ")+"  - toggle Stanford NetDB specific filter.\n"+
                    "numeric"     +(ptype==NUMERIC    ?" (on)":"     ")+"       - sets field type to numeric.\n"+
                    "domain"      +(ptype==DOMAIN_NAME?" (on)":"     ")+"        - sets field type to domain ( Stanford NetDB specific ).\n"+
                    "string"      +(ptype==STRING     ?" (on)":"     ")+"        - sets field type to string.\n"+
                    "table=<name>       - to set table to a given name. Current value is \""+table+"\"\n"+
                    "column=<name>      - to set column to a given name. Current value is \""+column+"\"\n"+
                    (settings_only?"":"(quit|exit)        - to quit program."));

    } // end of help(...)
}

/*
BEGIN REGRESSION TEST DATA
STRING ;a and b;                                                                -> ;(  ( select id from Record where name = 'a' )  INTERSECT  ( select id from Record where name = 'b' )  );
STRING ;a and b or c;                                                           -> ;(  (  ( select id from Record where name = 'a' )  INTERSECT  ( select id from Record where name = 'b' )  )  UNION  ( select id from Record where name = 'c' )  );
STRING ;a and (b or c );                                                        -> ;(  ( select id from Record where name = 'a' )  INTERSECT  (  ( select id from Record where name = 'b' )  UNION  ( select id from Record where name = 'c' )  )  );
STRING ;("\\666\\ 835-4355" and other) or b75*;                                 -> ;(  (  ( select id from Record where name = '\666\ 835-4355' )  INTERSECT  ( select id from Record where name = 'other' )  )  UNION  ( select id from Record where name LIKE 'b75%' ESCAPE '\' )  );
STRING ;a and not c;                                                            -> ;(  ( select id from Record where name = 'a' )  INTERSECT  ( select id from Record MINUS  ( select id from Record where name = 'c' )  )  );
STRING ;not "Bob*";                                                             -> ;( select id from Record MINUS  ( select id from Record where name LIKE 'Bob%' ESCAPE '\' )  );
STRING ;Bob or not D'Bob;                                                       -> ;(  ( select id from Record where name = 'Bob' )  UNION  ( select id from Record MINUS  ( select id from Record where name = 'D''Bob' )  )  );
STRING ;"Bob Dilan" or bust;                                                    -> ;(  ( select id from Record where name = 'Bob Dilan' )  UNION  ( select id from Record where name = 'bust' )  );
STRING ;not not "The Truth";                                                    -> ;( select id from Record MINUS  ( select id from Record MINUS  ( select id from Record where name = 'The Truth' )  )  );
STRING ;"Give me liberty" or "give me death" or * and "good ol\" cookie";       -> ;(  (  (  ( select id from Record where name = 'Give me liberty' )  UNION  ( select id from Record where name = 'give me death' )  )  UNION  ( select id from Record where name LIKE '%' ESCAPE '\' )  )  INTERSECT  ( select id from Record where name = 'good ol" cookie' )  );
STRING ;Not "that ^%#@#";                                                       -> ;( select id from Record MINUS  ( select id from Record where name LIKE 'that ^%#@#' ESCAPE '\' )  );
STRING ;first_name and not last\_name;                                          -> ;(  ( select id from Record where name LIKE 'first_name' ESCAPE '\' )  INTERSECT  ( select id from Record MINUS  ( select id from Record where name = 'last_name' )  )  );
STRING ;<html> or not </html>;                                                  -> ;(  ( select id from Record where name = '<html>' )  UNION  ( select id from Record MINUS  ( select id from Record where name = '</html>' )  )  );
STRING ;"^foo\\bar" oR ( ( jar-jar ) );                                         -> ;(  ( select id from Record where name = '^foo\bar' )  UNION  ( select id from Record where name = 'jar-jar' )  );
STRING ;not and c;                                                              -> ;ERROR;
STRING ;a b c;                                                                  -> ;ERROR;
STRING ;a and and;                                                              -> ;ERROR;
STRING ;a and "and";                                                            -> ;(  ( select id from Record where name = 'a' )  INTERSECT  ( select id from Record where name = 'and' )  );
STRING ;ab\* or ab\\*;                                                          -> ;(  ( select id from Record where name = 'ab*' )  UNION  ( select id from Record where name LIKE 'ab\\%' ESCAPE '\' )  );
STRING ;a"b;                                                                    -> ;ERROR;
STRING ;"Salmon_says :" and "[42]" or else?;                                    -> ;(  (  ( select id from Record where name LIKE 'Salmon_says :' ESCAPE '\' )  INTERSECT  ( select id from Record where name = '[42]' )  )  UNION  ( select id from Record where name LIKE 'else_' ESCAPE '\' )  );
STRING ;(("What is it\?"));                                                     -> ;( select id from Record where name = 'What is it?' );
STRING ;and OR end;                                                             -> ;ERROR;
STRING ;o'le or le?.;                                                           -> ;(  ( select id from Record where name = 'o''le' )  UNION  ( select id from Record where name LIKE 'le_.' ESCAPE '\' )  );
NUMERIC ;1 or 2 and not 3;                                                      -> ;(  (  ( select id from Record where name = 1 )  UNION  ( select id from Record where name = 2 )  )  INTERSECT  ( select id from Record MINUS  ( select id from Record where name = 3 )  )  );
NUMERIC ;1 or nothing;                                                          -> ;ERROR;
STRING ;/^a\/c/i;                                                               -> ;( select id from Record where REGEXP_LIKE(name, '^a/c', 'i') );
STRING ;/^a\/c/;                                                                -> ;( select id from Record where REGEXP_LIKE(name, '^a/c', '') );
STRING ;/^.*[[:digit:]]$/i;                                                     -> ;( select id from Record where REGEXP_LIKE(name, '^.*[[:digit:]]$', 'i') );
COLUMN ;lower(name);                                                            -> ;;
NETDB  ;aBc;                                                                    -> ;( select id from Record where lower(name) = 'abc' );
NETDB  ;/(A|b)/;                                                                -> ;( select id from Record where REGEXP_LIKE(name, '(A|b)', '') );
NETDB  ;/(a|B)/i;                                                               -> ;( select id from Record where REGEXP_LIKE(name, '(a|B)', 'i') );
NETDB  ;/ads/x;                                                                 -> ;ERROR;
COLUMN ;name_lc;                                                                -> ;;
NETDB  ;aBc;                                                                    -> ;( select id from Record where name_lc = 'abc' );
NETDB  ;/(A|b)/;                                                                -> ;( select id from Record where REGEXP_LIKE(name, '(A|b)', '') );
NETDB  ;/(a|B)/i;                                                               -> ;( select id from Record where REGEXP_LIKE(name, '(a|B)', 'i') );
END REGRESSION TEST DATA
*/
