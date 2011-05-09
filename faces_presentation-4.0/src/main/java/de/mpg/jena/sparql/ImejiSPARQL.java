package de.mpg.jena.sparql;

import java.util.LinkedList;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.tdb.TDB;

import de.mpg.jena.ImejiJena;
import de.mpg.jena.ImejiRDF2Bean;

public class ImejiSPARQL
{
	
	/**
	 * Exectute over the whole Imeji DataSet
	 * @param <T>
	 * @param query
	 * @param c
	 * @return
	 */
	public static <T> LinkedList<T> execAndLoad(String query, Class<T> c)
	{
		Query q = QueryFactory.create(query, Syntax.syntaxARQ);
		QueryExecution qexec  = QueryExecutionFactory.create(q, ImejiJena.imejiDataSet);
		qexec.getContext().set(TDB.symUnionDefaultGraph, true);
		Model m = ImejiJena.imejiDataSet.getNamedModel(ImejiJena.getModelName(c));
		ImejiRDF2Bean reader = new ImejiRDF2Bean(m);
		LinkedList<T> beans = new LinkedList<T>();
        try 
        {
                m.enterCriticalSection(Lock.READ);
                ResultSet results = qexec.execSelect();
               // ResultSetFormatter.out(System.out, results) ;
                for (;results.hasNext();)beans.add(reader.load(c, resource(results).toString()));
                return beans;
        } finally 
        {
                m.leaveCriticalSection();
                qexec.close();
        }
	}
	
	/**
	 * Search only over a model
	 * @param <T>
	 * @param m
	 * @param query
	 * @param c
	 * @return
	 */
	public static <T> LinkedList<T> execAndLoad(Model m, String query, Class<T> c)
	{
		ImejiRDF2Bean reader = new ImejiRDF2Bean(m);
		Query q = QueryFactory.create(query, Syntax.syntaxARQ);
		QueryExecution qexec  = QueryExecutionFactory.create(q, m);
		LinkedList<T> beans = new LinkedList<T>();
        try 
        {
                m.enterCriticalSection(Lock.READ);
                ResultSet results = qexec.execSelect();
                for (;results.hasNext();) beans.add(reader.load(c, resource(results).toString()));
                return beans;
        } finally 
        {
                m.leaveCriticalSection();
                qexec.close();
        }
	}
	
	/**
	 * Execute a query but doesn't load the object.
	 * Very fast. To be tested
	 * @param query
	 * @param c
	 * @return
	 */
	public static LinkedList<String> exec(String query)
	{
		Query q = QueryFactory.create(query, Syntax.syntaxARQ);
		QueryExecution qexec  = QueryExecutionFactory.create(q, ImejiJena.imejiDataSet);
		qexec.getContext().set(TDB.symUnionDefaultGraph, true) ;
		LinkedList<String> resultList = new LinkedList<String>();
        try 
        {
                ResultSet results = qexec.execSelect();
                for (;results.hasNext();) 
                {
                	for (;results.hasNext();) resultList.add(resource(results).toString());
                }
                return resultList;
        } finally 
        {
                qexec.close();
        }
	}
	
	/**
	 * Example: SELECT ?s count(DISTINCT ?s) WHERE { ?s a <http://imeji.mpdl.mpg.de/image>}
	 * @param query
	 * @param model
	 * @return
	 */
	public static int execCount(String query)
	{
		Query q = QueryFactory.create(query, Syntax.syntaxARQ);
		QueryExecution qexec  = QueryExecutionFactory.create(q, ImejiJena.imejiDataSet);
		qexec.getContext().set(TDB.symUnionDefaultGraph, true) ;
		ResultSet rs = qexec.execSelect();
		int count = 0;
		if (rs.hasNext())
		{
			count = rs.next().getLiteral("?.1").getInt();
		}
		qexec.close();
		return count;
	}
	
	public static int execCount2(String query)
	{
		Query q = QueryFactory.create(query, Syntax.syntaxARQ);
		QueryExecution qexec  = QueryExecutionFactory.create(q, ImejiJena.imejiDataSet);
		qexec.getContext().set(TDB.symUnionDefaultGraph, true) ;
        try 
        {
                ResultSet results = qexec.execSelect();
                for (;results.hasNext();) resource(results);
                return results.getRowNumber();
        } finally 
        {
        	 qexec.close();
        }
	}
	
	public static Model execConstruct(String query)
	{
		Query q = QueryFactory.create(query, Syntax.syntaxARQ);
		QueryExecution qexec  = QueryExecutionFactory.create(q, ImejiJena.imejiDataSet);
		qexec.getContext().set(TDB.symUnionDefaultGraph, true) ;
		Model mod = qexec.execConstruct();
		return mod;
	}
	
	private static Resource resource(ResultSet results) 
	{
        return results.nextSolution().getResource("s");
	}
}
