/************************************************************************
 *
 * PostFilter.java -
 *
 * $Id: PostFilter.java,v 1.2 2008/07/29 21:24:03 priimak Exp $
 *
 *-----------------------------------------------------------------------
 * Copyright (c) 2008, Board of Trustees, Leland Stanford Jr. University
 ************************************************************************/
package stanford.netdb.utils;

/**
 * When generating SQL, after parsing we come to the point of building where expression,
 * it is possible to use instances of this class to transform generating expression.
 * For example, instead of<br><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;column LIKE 'field'<br><br>
 * will be generated<br><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;post_filter.column_filter(..., column, field) LIKE 'post_filter.field_filter(..., column, field)'<br><br>
 *
 * This can be used with LIKE, =, and REGEXP_LIKE expression.
 *
 * Default instance of this class does not perform any transformations on any of the fields.
 */
public class PostFilter extends Object {

    /**
     * Performs transformation of the field value after
     * parsing of the original source string completed.
     *
     * @param expr        one of {'LIKE', 'REGEXP_LIKE', '='}
     * @param table       table against which WHERE expression will be build
     * @param column      column name
     * @param field       field value to be used in the 'column LIKE expression'
     */
    public String field_filter(String expr, String table, String column, String field)
        throws Exception
    {
        return field;
    }

    /**
     * Performs transformation of the column name after
     * parsing of the original source string completed
     *
     * @param expr        one of {'LIKE', 'REGEXP_LIKE', '='}
     * @param table       table against which WHERE expression will be build
     * @param column      column name
     * @param field       field value to be used in the 'column LIKE expression'
     */
    public String column_filter(String expr, String table, String column, String field)
        throws Exception
    {
        return column;
    }
}
