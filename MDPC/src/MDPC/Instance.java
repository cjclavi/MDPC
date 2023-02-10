package MDPC;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Scanner;

import MDPC.Models;
//import ilog.concert.IloException;
//import ilog.cplex.IloCplex;
import ilog.concert.*;
import ilog.cplex.*;

public class Instance {			
	
	// Parameters of instance (DATASETS SIZES)
	public String name;
	public static int NP;  
	public static int ND;
	public static int NC;
	public static int Co;
	
	// Parameters of instance (CAPACITY LEVELS)
	public static int NL;
	public double lvQ1;
	public double[] LCost_1;
	public double[] lvQ; 
	public int[][] maxNL;
	
	// Parameters of instance (demand, costs, network)
	public double[][] Dem;
	public double[] FCost;
	public double[][] LCost;
	public double[][] SCost;
	public boolean[][] Slinks;		
	public int [] owners;  
	//public int [] depots; // depots of each company
	public static double[] DFCost; //default costs of instance
	public static double[] DLCost; 

	
	
	// Parameters of instance (CONTRACTS CONDITIONS)
	public int mct; // contract duration
	public double [][] mpc;  // To Determine
	public double mpcParam; 
	public double maxMPC;
	public double minMPC;
	public double spotParam;
	
	// Parameters/data to determine from the instance (Min. Costs)
	public double minDistCost;
	public double minCarCost;
	public double minOpCost;
	public double minMPCCost;
	public double minTotalCost;
	
	
	
	/****************************************************************************************************************
	 * Read the instance file: Periods, companies, mct, customers, depots and links data in that order
	 * @param nFile The input File with the data
	 * @throws FileNotFoundException
	 ***************************************************************************************************************/
	public Instance( File nFile ) {		
		try {
			newInstance( nFile );
			String longname = nFile.getName( );
			int ch = 0; int i = -1;; int j = -1;
			while ( ch < longname.length( ) ) {
				if (longname.charAt( ch ) == 'p' && longname.charAt( ch+1 ) == '-') i=ch;
				if (longname.charAt( ch ) == 'r') j=ch;
				ch++;
			}
			//name = longname.substring (0,i+1) + ")-" + longname.substring (j,j+5);
			name = longname.substring (j,j+5) + "-" + longname.substring (0,i+1) + ")"  ;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	
	
	public void newInstance( File nFile ) throws FileNotFoundException {
		Scanner reader = new Scanner( nFile );
		reader.next( ); // return the 'periods' label
		NP = reader.nextInt( ); reader.next( ); //return the 'companies' label
		Co = reader.nextInt( ); reader.next( ); // return the 'customers' label		
		readCustomers( reader );
		readDepots( reader  );
		readLinks( reader );
		setContractConditions_mpc( 0.10 ); // % of total business 
	}
	
	
	
	public void printInstanceData( ) {
		// Table of Max. levels per depot
		printMaxLevels( );
		// Table of purchase commitment terms :
		printMPC_Table( );
		// Print data of an instance :
		computeMinCosts( ); 
		printMinCosts( );
	}
	
	
	
	/******************************************************************************************
	 * Method that reads the data of customers from a file
	 *****************************************************************************************/
	public void readCustomers( Scanner reader ){
		NC = reader.nextInt( ); 
		Dem = new double[NC][NP];
		for(int k = 0; k < NC; k++){
			for(int t = 0; t < NP; t++){				
				Dem[k][t] = reader.nextDouble( ); 
			}
		}
		reader.next( );
	}
	
	
	
		
	/****************************************************************************************
	 * Method that reads the data of depots from a file
	 *****************************************************************************************/
	public void readDepots( Scanner reader ){
		ND = reader.nextInt( ); reader.next( );		
		lvQ1 = reader.nextDouble( );
		owners = new int[ND];
		// Fixed Cost per depot ( Fi )
		FCost = new double[ND];  
		DFCost = new double[ND];  // default fixed cost 
		DLCost = new double[ND]; // default long vehicle cost 
		LCost_1 = new double[ND];
		for(int i = 0; i < ND; i++){
			owners[i] = reader.nextInt( );
			FCost[i] = reader.nextDouble( );
			DFCost[i] = FCost[i];
			LCost_1[i] = reader.nextDouble( );
			DLCost[i] = LCost_1[i];
		}
		reader.next( );
	}	
	
	/******************************************************************************************
	 * Method to read the data of links from a file  
	 *****************************************************************************************/
	public void readLinks( Scanner reader ){
		//int links = reader.nextInt( );
		SCost = new double[ND][NC];
		Slinks = new boolean[ND][NC];
		reader.next( );
		while( reader.hasNext( ) ){
			int i = reader.nextInt( );
			int k = reader.nextInt( );
			Slinks[i][k] = true;
			double stcost = round( reader.nextDouble( ), 2 );
			SCost[i][k] = stcost;
		}
		// Determine the number of levels (max vehicles to a depot), the Capacity and Cost per level
		maxNL = new int[ND][NP];
		NL = determineLevels( );
		lvQ = new double[NL]; 
		LCost = new double[ND][NL]; // check
		for(int a = 0; a < NL; a++){
			lvQ[a] = lvQ1 * (a+1);
			for(int i = 0; i < ND; i++) {
				double discount = ( 1 - (a /100.0) );
				if (discount > 0.8) {
					LCost[i][a] = LCost_1[i] * ( a+1 ) * discount;
					
				} else {
					LCost[i][a] = LCost_1[i] * ( a+1 ) * 0.8;
					
				}
			}
		}
	}
	
	
	
	/***********************************************************************************************************************
	 * DETERMINATION OF LEVELS ( |L| )  
	 * *********************************************************************************************************************/	
	
	public int determineLevels( ){		
		// LEVELS ::: sumar el depot con mayor potencial demanda y dividir por la capacidad
		int maxl = 0; // maximum allocated demand found
		//String format = "%5s";
		for(int t = 0; t < NP; t++){ // System.out.print( t+":|");
			// int carr = 1;
			for(int i = 0; i < ND; i++){
				int lev = determineLevelsPerDepot( i, t );
				maxNL[i][t] = lev;
				//if(owners[i] != carr) {
				//	System.out.print("|" );
				//	carr++;
				//}
				//System.out.format(format, maxNL[i][t] + " |" );
				if(lev > maxl)	maxl = lev;
			} // System.out.println( );
		}	
		return maxl; 
	}	
	
	public void printMaxLevels( ) {
		String format = "%5s";
		System.out.println( "Table of maximum levels per depot/period ");
		for(int t = 0; t < NP; t++){ System.out.print( t+":|");
			int carr = 1;
			for(int i = 0; i < ND; i++){
				if(owners[i] != carr) {
					System.out.print("|" );
					carr++;
				}
				System.out.format(format, maxNL[i][t] + " |" );
			}System.out.println( );
		}
		System.out.println("max NL: "+ NL );
		System.out.println( );
	}
	
	
	
	public int determineLevelsPerDepot( int i, int t ){		
		double cov_demand = 0; 	// potential allocated demand to depot i
		for( int k = 0; k < NC; k++ ){
			if( Slinks[i][k] == true )
				cov_demand += Dem[k][t];	
		}
		int levels = (int) ( cov_demand / lvQ1 ) + 1; //  +1 level because (a < in.maxNL[i][t])
		return levels; 
	}
	
	
	/*****************************************************************************************************************
	 *>>>> Methods that set CONTRACTS CONDITIONS
	/*********************************************************************************************/
	
	public void setContractConditions_mpc( double mpc_p ) {
		mpcParam = mpc_p; // double or integer
		maxMPC = 0;  // maximum 'mpc' among carriers and periods.
		minMPC = Integer.MAX_VALUE; // minimum 'mpc' among carriers and periods. 
		spotParam = 0;  
		// Determine the MPC for each 'potential' carrier at each period.
		determineVMPC( );
		
	}	
	
	public void setContractConditions_mct( int nMct ) {
		mct = nMct;
	}	
	

	public void printMPC_Table( ) {
		String format = " %10s ";
		System.out.println( "MPC Table" ); // Table of purchase commitment terms :
		for(int e = 0; e < Co; e++){
			System.out.print( "Car"+ e +":" );
			for(int t = 0; t < NP; t++){
				System.out.format( format,  round( mpc[e][t], 2 ) +" | ");
				//System.err.print( round( mpc[e][t], 2 ) +" | ");
			}
			System.out.println( );
		}
		// Print the max and min mpc along the planning horizon
		System.out.println( "max. MPC : "+ round( maxMPC, 2 ) );
		System.out.println( "min. MPC : "+ round( minMPC, 2 ) );	
		System.out.println( );	
	}

	
	
	/***********************************************************************************
	 *  Fixed MPC for all carriers along T =>
	 *  MPC: media entre suma de fixed cost y variable cost de un camion	
	 ***********************************/
	
	public void determineFMPC( ) {	
		mpc = new double[Co][NP];
		double avg = 0;
		for(int i = 0; i < ND; i++){
			avg += FCost[0]+ LCost[i][0]/ND; 
		}				
		//double min = Double.MAX_VALUE; double max = 0;
		for(int e = 0; e < Co; e++){			
			for(int t = 0; t < NP; t++){				
				mpc[e][t] = avg; 	// ? only for same fixed cost		
			}						
		}			
	}	
	
	
	/********************************************************************************
	 *  Variable MPC 
	 *  MPC: Percentage of the min. cost,  given complete demand assignment to carrier e, in t.
	 *************************************/
	
	public void determineVMPC( ) {
		Models mod = new Models( );
		mpc = new double[Co][NP];
		minMPC = Double.MAX_VALUE; maxMPC = 0;
		for(int e = 0; e < Co; e++){			
			for(int t = 0; t < NP; t++){
				// Min. cost and 'solution' to supply all possible customers assigned to carrier e.
				IloCplex cp = mod.createISP_EP( this, t, e+1 );	
				cp.setOut( null );
				try {                               
					if( cp.solve( ) ){
						mpc[e][t] = cp.getObjValue( ) * mpcParam;						
						if( mpc[e][t] > maxMPC )
							maxMPC = mpc[e][t];
						if( mpc[e][t] < minMPC )
							minMPC = mpc[e][t];
					}										
				} catch (IloException e1) {
					e1.printStackTrace( );
				}
			}						
		}
	}

	
	
	/********************************************************************************************************
	 * Methods that compute the minimum/maximum costs given an instance
	 * ******************************************************************************************************/
	
	public void printMinCosts( ) {
		String f1 = "%-23s %7s";
		System.out.format ( f1, "Min. Carriers Costs:", round( minCarCost,2 ) );
		System.out.println( "  (Op: " + round( minOpCost,2 ) + " | MPC: "+ round( minMPCCost,2 ) + " )" );		
		System.out.format ( f1, "Min. Distribution Cost:", round( minDistCost,2 ) );
		System.out.println( );	
		System.out.format ( f1, "Min. Total Cost:", round( minTotalCost,2 ) );
		System.out.println( );	
	}
	
	public void computeMinCosts( ) {
		computeMinimumCarrierCost( );
		computeMinimumDistributionCost( );
		minTotalCost = minDistCost + minCarCost;
	}
	

	/**************************************************
	 * Compute the minimum distribution cost of a given Instance
	 * MinDC : 
	 */
	public void computeMinimumDistributionCost( ) {
		// Closest Depot Solution (for each customer k) 
		minDistCost = 0;		
		for(int k = 0; k < NC; k++){
			int closestDepot = -1; double MinDCost = Double.MAX_VALUE;
			// Find the cheapest depot to send one unit to customer k
			for(int i = 0; i < ND; i++) {
				if(Slinks[i][k] == true && SCost[i][k] < MinDCost){
					closestDepot = i;
					MinDCost = SCost[i][k];
				}
			}
			for(int t = 0; t < NP; t++)
				minDistCost += (Dem[k][t] * MinDCost);
			
		}
	}

	
	/*************************************************
	 * Compute the minimum carrier's costs of a given Instance
	 * MinCC
	 */
	public void computeMinimumCarrierCost( ) {
		minCarCost = 0;
		Models m = new Models( );
		// Compute the min. carriers' operational costs
		minOpCost = 0;
		// solve the model without MPC: only carriers (TL) costs
		minOpCost = m.minOpCost( this );
		// Compute the carrier's MPC Costs.
		minMPCCost = 0;
		// add up the MPC of the carriers with the least cost
		minMPCCost = m.minMPCCost( this );
		
		// Select the maximum between this two.
		minCarCost = Math.max( minOpCost, minMPCCost );
	}
	
	
	
	// Determines the cheapest depot to ship through (from the set I_e of carrier e)
	// param e from 1 to Co.
	public double getCheapestDepot( int e ) {
		int depot = -1; 
		double cheapest = Integer.MAX_VALUE; 
		for(int i = 0; i < ND; i++){	
			  if( owners[i] == e+1 ) {
				  if( FCost[i] + LCost[i][0] < cheapest ) {
					 depot = i; cheapest =  FCost[i] + LCost[i][0];
				  }
			  }
		}
		return depot;
	}
	
	
	
	// Determines the cheapest cost to ship through, up to customer k (per volume unit)
	public double getLowestCost( int k ) {
		double lc = Double.MAX_VALUE;
		for(int i = 0; i < ND; i++){
			if(Slinks[i][k] == true){
				if( SCost[i][k] < lc )
					lc = SCost[i][k];				
			}
		}			
		return lc;
	}
	
	
	
	// Determines the number of depots of carrier e
	public int getDepots( int e ) {
		int depots = 0; 
		for(int i = 0; i < ND; i++){	
			 if( owners[i] == e+1 )  depots++;
		}
		return depots;
	}
	
	public static double round(double value, int places) {
	    if ( places < 0 ) throw new IllegalArgumentException( );
	    long factor = (long) Math.pow(10, places);
	    value = value * factor;
	    long tmp = Math.round(value);
	    return (double) tmp / factor;
	}
	
	
	public void printName( ) {
		//name = name.substring (0,11) + ")-" + name.substring (43,47);
		String nameLine = "____________________________________ Instance: "+ name + " ________________________________________";
		System.out.println( );
		System.out.println( nameLine );
		System.out.println( );
		System.out.println( "MCT: "+mct + "  - SM: "+ spotParam );
	}

	
	public void printName( String arg ) {
		//name = name.substring (0,11) + ")-" + name.substring (43,47);
		String nameLine = "____________________________________ Instance: "+ name + " ________________________________________";
		System.out.println( );
		System.out.println( nameLine );
		System.out.println( );
		System.out.println( "MCT: "+mct + "  - SM: "+ spotParam + " - "+ arg);
	}

	/*****
	 * Set the prices of carriers to transport to each facility at each capacity (truck) level
	 * @param percentage
	 */
	public void setPrices(double percentage) {
		spotParam = percentage;
		for(int i = 0; i < ND; i++){
			FCost[i] = DFCost[i] * (1 + percentage);
			for(int a = 0; a < NL; a++){
				double discount = ( 1 - (a /100.0) );
				if (discount > 0.8)
					LCost[i][a] = ( DLCost[i] * (1+percentage) ) * ( a+1 ) * discount;
				else
					LCost[i][a] = ( DLCost[i] * (1+percentage) ) * ( a+1 ) * 0.8;
			}
		}
	}
	
	
	/**
	 * returns true if company e can deliver to customer k, false otherwise
	 */
	public boolean companyCanDeliver(int e, int k) {
		boolean connected = false;
		for(int i = 0; i < ND; i++){
			if( owners[i] == e+1 && Slinks[i][k] == true )
			connected = true;
		}
		return connected;
	}
	
	
	
	/**
	 * returns the season the period t belongs to.
	 */
	public String getSeason( int t ) {
		String season = "";
		double factor = (double) t / NP ; //System.out.print(factor);
		if( factor  <  0.33 ) season = "LOW";
		else if( factor  >=  0.33 &&  factor  <  0.66 ) season = "HIGH";
		else if( factor  >= 0.66 && factor  <=  1 ) season = "MEDIUM";
		return season;
	}
}
