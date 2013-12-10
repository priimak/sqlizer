header {
package stanford.netdb.utils;
}

// This class builds AST.
class StringsParser extends Parser;

options {
    buildAST = true;
    defaultErrorHandler = false;
}

expr
    : block ( (AND^|OR^) block )* (END)?;

block
    : (NOT^) block
    | STRING
    | REGEX
    | LPAREN! expr RPAREN!;

// Given AST this class produces conditional expression to be
// added to select statement.
class FieldTreeParser extends TreeParser;

options {
    defaultErrorHandler=false;
}

{
    // indicates table against which sql will be build
    public String table  = "Record";

    // name of the column against which we are building sql
    public String column = "name";

    // by setting this value to true we ensure that strings are numbers
    public boolean match_numbers = false;

    // by setting this variable to true we can ensure search in subdomains.
    public boolean domain_name = false;

    // post filter for each value to be matched. The result of
    // post_filter.filter(...) call becomes value to be matched.
    public Object post_filter = null;

    // if not null then when called the sql is built according to the
    // following algorithm:
    //       select id from table
    //       where `expression_builder.filter(table, column, field)`
    public FieldPostFilter expression_builder = null;

    String buildRegexCondition(String value) throws RecognitionException, Exception {
        value = Field2SQL.unescape(value);
        value = value.replaceAll("'", "''");
        int length = value.length();

        int cut_out = 1;
        String regex_modes = "";
        if( value.matches("^.*\\/i") ) {
            cut_out = 2;
            regex_modes = "i";
        }

        String m_column = column;
        String m_value = value.substring(1, value.length() - cut_out);
        if( post_filter instanceof PostFilter ) {
            m_column =
                ((PostFilter)post_filter).column_filter("REGEXP_LIKE", table, column, m_value);
            m_value =
                ((PostFilter)post_filter).field_filter("REGEXP_LIKE",  table, column, m_value);
        }

        return "REGEXP_LIKE(" + m_column + ", '" +  m_value +
            "', '" + regex_modes + "')";
    }

    String buildStringCondition(String value) throws RecognitionException, Exception {
        value = Field2SQL.unescape(value);
        String svalue     = "";
        String nvalue     = "";
        int length        = value.length();
        boolean escaped   = false;
        boolean like_expr = false;
        for( int i = 0; i < length; i++ ) {
            String cchar = value.substring(i, i+1);
            boolean end = i==length-1;
            String nchar = end?"":value.substring(i+1, i+2);
            if( cchar.equals("\\") ) {
                escaped = escaped?false:true;
                svalue += cchar;
            } else {
                if( cchar.equals("*") && !escaped ) {
                    svalue += "%";
                    like_expr = true;
                } else if( cchar.equals("?") && !escaped ) {
                    svalue += "_";
                    like_expr = true;
                } else {
                    if( ( cchar.equals("%") || cchar.equals("_") ) && !escaped )
                        like_expr = true;
                    svalue += cchar;
                    escaped = false;
                }
            }
        }
        if( escaped ) // escape char not followed by any character
            throw new RecognitionException("Illegal use of escape character.");

        if( svalue.startsWith("\"") && svalue.endsWith("\"") )
            svalue = svalue.substring(1, svalue.length()-1);

        if( ( domain_name || column.matches("^.*fullname.*$") ) && svalue.indexOf(".") < 0 ) {
            svalue += ".%";
            like_expr = true;
        }

        if( domain_name && !svalue.endsWith(".") && !svalue.endsWith("%") )
            svalue += ".";

        length = svalue.length();

        if( !like_expr ) { // = expr
            escaped = false;
            for( int i = 0; i < length; i++ ) {
                String cchar = svalue.substring(i, i+1);
                if( escaped ) {
                    nvalue += cchar.equals("'")?"''":cchar;
                    escaped = false;
                } else if( cchar.equals("\\") )
                    escaped = true;
                else
                    nvalue += cchar.equals("'")?"''":cchar;
            }
            if( expression_builder != null )
                nvalue = expression_builder.filter(table, column, nvalue);
            else {
                if( post_filter instanceof PostFilter ) {
                    String mvalue =
                        ((PostFilter)post_filter).field_filter("=", table, column, nvalue);
                    nvalue = ((PostFilter)post_filter).column_filter("=", table, column, nvalue) +
                        " = " + (match_numbers?mvalue:("'" + mvalue + "'"));

                } else if( post_filter instanceof FieldPostFilter ) {
                    String mvalue = ((FieldPostFilter)post_filter).filter(table, column, nvalue);
                    nvalue = column + " = " + (match_numbers?mvalue:("'" + mvalue + "'"));
                }
            }

        } else { // like expr
            escaped = false;
            for( int i = 0; i < length; i++ ) {
                String cchar = svalue.substring(i, i+1);
                if( escaped ) {
                    if( cchar.equals("'") )
                        nvalue += "''";
                    else if( cchar.equals("\\") || cchar.equals("_") || cchar.equals("%") )
                        nvalue += "\\"+cchar;
                    else
                        nvalue += cchar;
                    escaped = false;
                } else if( cchar.equals("\\") )
                    escaped = true;
                else
                    nvalue += cchar.equals("'")?"''":cchar;
            }

            if( expression_builder != null )
                nvalue = expression_builder.filter(table, column, nvalue);
            else {
                if( post_filter instanceof PostFilter ) {
                    nvalue =
                        ((PostFilter)post_filter).column_filter("LIKE", table, column, nvalue) +
                        " LIKE '" +
                        ((PostFilter)post_filter).field_filter("LIKE", table, column, nvalue) +
                        "' ESCAPE '\\'";

                } else if( post_filter instanceof FieldPostFilter ) {
                    nvalue = column+" LIKE '" +
                        ((FieldPostFilter)post_filter).filter(table, column, nvalue) +
                        "' ESCAPE '\\'";
                }
            }
        }
        return nvalue;
    }

    String value(String src) {
        if( src.startsWith("'") && src.endsWith("'") )
           return src.substring(1, src.length()-1);
        return src;
    }
}

expr returns [ String result ]
{
    String left          = null;
    String right         = null;

    result = "";
}
    :  #( AND left=expr right=expr ) {
            result = " ( "+left+" INTERSECT "+right+" ) ";
        }
    |  #( OR left=expr right=expr ) {
            result = " ( "+left+" UNION "+right+" ) ";
        }
    |  #( NOT right=expr ) {
            result = " ( select id from "+table +" MINUS "+right+" ) ";
        }
    | value:STRING
        {
            if( match_numbers )
                result =
                    " ( select id from "+table+" where "+column+" = "+
                    Integer.parseInt(value.toString())+" ) ";
            else {
                try {
                    result =
                        " ( select id from "+table+" where "+
                        buildStringCondition(value.toString())+" ) ";
                } catch(Exception ex) {
                    throw new RecognitionException(ex.getMessage());
                }
            }
        }
    | rvalue:REGEX
        {
            try {
                result =
                    " ( select id from "+table+" where "+
                    buildRegexCondition(rvalue.toString())+" ) ";
            } catch(Exception ex) {
                throw new RecognitionException(ex.getMessage());
            }
        }


    ;

// This class produces stream of tokens { STRING, LPAREN, RPAREN, END }
class StringsLexer extends Lexer;

options {
        defaultErrorHandler=false;
}

{
    // By setting this value to true we ensure that strings are numbers
    public boolean match_numbers = false;
}

STRING
    : ( '"' (ESC|CCSET|'('|')'|'/'|' '|'\t')* '"' | (ESC|CCSET) (ESC|CCSET|'/')* )
        { String textLc = getText().toLowerCase();
          if( textLc.matches("^and\\s*") || textLc.matches("^or\\s*") || textLc.matches("^not\\s*") ) {
              $setText(textLc);
              if( textLc.equals("and") )
                  $setType(AND);
              else if( textLc.equals("or") )
                  $setType(OR);
              else if( textLc.equals("not") )
                  $setType(NOT);
          } else {
              if( match_numbers )
                  Integer.parseInt(textLc);
          }
        }
    ;

REGEX
    : ( '/' (CCSET|'\\'|'('|')'|' '|'\t')* '/' ('i'|) )
        { String textLc = getText().toLowerCase();
          if( textLc.matches("^and\\s*") || textLc.matches("^or\\s*") || textLc.matches("^not\\s*") ) {
              $setText(textLc);
              if( textLc.equals("and") )
                  $setType(AND);
              else if( textLc.equals("or") )
                  $setType(OR);
              else if( textLc.equals("not") )
                  $setType(NOT);
          } else {
              if( match_numbers )
                  Integer.parseInt(textLc);
          }
        }
    ;

LPAREN   :  '(';
RPAREN   :  ')';
END      :  ';';

// white space token
protected
ESC      :   '\\' ( '*'|'%'|'?'|'_'|'\\'|'"' ) ;

// in regex expressions we un-escape only \/ everything else is passed verbatim
protected
RESC     :   '\\' (CCSET|'\\'|'/'|'('|')'|' '|'\t') ;

protected
CCSET    :   ('a'..'z'|'A'..'Z'|'0'..'9'|'\''|'~'|'!'|'@'|'#'|'$'|'%'|'^'|'&'|'*'|'_'|'+'|'<'|'>'|'?'|'`'|'-'|'='|','|'.'|'['|']'|'{'|'}'|'|'|':');

WS        : ( ' ' | '\t' | '\n' | '\r' )+
        { $setType(Token.SKIP); };
