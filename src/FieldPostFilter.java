/************************************************************************
 *
 * FieldPostFilter.java -
 *
 * $Id: FieldPostFilter.java,v 1.4 2008/07/29 21:23:03 priimak Exp $
 *
 *-----------------------------------------------------------------------
 * Copyright (c) 2006, Board of Trustees, Leland Stanford Jr. University
 ************************************************************************/
package stanford.netdb.utils;

/**
 * This class has double usage. When
 * used in {@link Field2SQL#buildField(int, String, String, String, String, FieldPostFilter) Field2SQL.buildField(..., expr_builder)}
 * passed as expr_builder variable, SQL is built as<br><br>
 *
 * &nbsp;&nbsp;&nbsp;&nbsp;select id from table where `expr_builder.filter(table, column, field)`<br><br>
 *
 * When used in {@link Field2SQL#parseField(int, String, String, String, String, Object) Field2SQL.parseField(..., Object post_filter)}
 * passed as post_filter, SQL is built as<br><br>
 *
 * &nbsp;&nbsp;&nbsp;&nbsp;select id from table where column {@literal <expr>} `((FieldPostFilter)post_filter).filter(table, column, field)`<br><br>
 * , where {@literal <expr>} is '=' or 'LIKE'. <br><br>
 *
 * In both cases it is not applied for REGEXP_LIKE expressions.
 */
public class FieldPostFilter extends Object {

    /**
     * Performs transformation of the field value or entire 'WHERE' expression after
     * parsing of the original source string completed. See above for more details.
     * Default instance performs no transformations on the field value.
     */
    public String filter(String table, String column, String field) throws Exception {
        return field;
    }
}
