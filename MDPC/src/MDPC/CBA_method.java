package MDPC;


import java.awt.List;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import MDPC.Instance;
import ilog.concert.*;
import ilog.cplex.*;
//import MDPC.IloException;
//import MDPC.IloCplex;
//import MDPC.IloLinearNumExpr;
//import MDPC.IloNumVar;

/**
 * Clase con metodos para resolver una instancia del problema MDPC
 * CBA: Resuelve el problema MDPC por medio de Combinatorial Benders
 */
public class CBA_method extends Solution_method {
	
	/***********************************************************************************************
	 * ATTRIBUTES
	 ************************************************************************************************/
		
		//----- CBA attributes -------
		private Instance in;  							// initialize in solveMDPC()
		private IloCplex master;  						// initializze in setRMP()
		private IloCplex sub;							// initialize in solveSP_EP()
		private IloNumVar[][] Alphas; //DV				// initializze in setRMP()
		private IloNumVar[][][] V;    //DV 				// initializze in setRMP()
		private IloNumVar [][][] q; 					// initializze in setRMP()
		private IloNumVar [][] Cost; 					// initializze in setRMP()
		private String status; // Optimal, Feasible, Infeasible, Unsolved
		
		//----- RMP attributes -------
		private double advLB; // best found value obtained during RMP (not optimal)
		private double advGap;
		private double RMP_LB; // if not finished
		
		
		//------ variables DURING resolution --------
		private int[][] tempAlphas; // every time the CB routine is called 
		private int[][] tempBetas;  // every time the CB routine is called 
		private double[][] betaStorage; 
		private int ST_CAP;
		private int cbIter;  // Number of CB iterations. ( alphas checked )
		private int spIter;  // Number of SP iterations. ( alphas checked )
		private int[] spIter_ep; // Number of times a sub-problem is solved - per period.
		
		
	/***********************************************************************************************
	 * CONSTRUCTOR
	 * Crea una instancia de la clase CBA_Method que puede llamar o correr los metodos de solucion
	 **************************************************/
		
		public CBA_method( Instance in ){
			super( in );
			//-------------------
			this.in = null;
			Z_UB = Double.MAX_VALUE;  // updated at the callback
			advLB = Double.MAX_VALUE; // best found value of the RMP
			advGap = -1;
			RMP_LB = 0;
			//----------------------
			tempAlphas = new int[in.Co][in.NP];
			tempBetas = new int[in.Co][in.NP];
			ST_CAP = (int) Math.pow (2, in.Co);
			// System.out.println ( "size of storage :"+ ST_CAP );
			betaStorage = new double[in.NP][ST_CAP];
			cbIter = 0;
			spIter = 0;
			spIter_ep = new int[in.NP];
		}
	
		
	/*******************************************************************************************************************************
	 * METHODS
	 ********************************************************************************************************************************/
	
		
	/*****************************************************************
	 * Main Problem (Integer V_{i,a})
	 * @throws IloException  
	 *******************/
	public void solveMDPC( Instance instance, int st ) throws IloException {	
		this.in = instance;
		long start = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		// create object (master) with the RMP
		setRMP( );
		// set callback and model parameters --> IntegerSubProblem
		setCallback( );
		long finish1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
		// solve Master Problem
		master.solve( );
		long finish2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
		
		//--------------- Copy (best found) solution values ------>>>>>>
		for(int t = 0; t < in.NP; t++) { 
			int[ ] tBetas = new int[in.Co];
			for( int e = 0; e < in.Co; e++ )
				tBetas[e] = betas[e][t];
			solveSP_EP( tBetas , t, "collect" );
		} 
		Z_gap = (Z_UB - Z_LB)/ Z_UB;  
		advGap = (Z_UB - advLB)/ Z_UB; 
		sol_time = finish2 - start; 
		setup_time = finish1 - start; 
		
		/***************************************** CBA OUTPUT *****************************************/
		printOutput_1( in, "CBA" );
		System.out.println( "Total CBs :"+ cbIter );
		System.out.println( "Total SPs :"+ spIter );
		for(int t = 0; t < in.NP; t++)
		System.out.println( "SP "+ t +  ": " + spIter_ep[t]+ " / " + (int) Math.pow(2, in.Co) );		
	}
	
	
	
	/*******************************************
	 * set the Relaxed Master Problem (Relaxed V_{i,a})
	 * configure the MILP 'master'
	 */
	public void setRMP( ) {
		try {					
			master = new IloCplex( );
			/********************************** VARIABLES ***************************************/
			/************************************************************************************/												
			q = new IloNumVar[in.ND][in.NC][ ];		
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true)
						q[i][k] = master.numVarArray( in.NP, 0, Double.MAX_VALUE );
				}
			}
			V = new IloNumVar[in.ND][in.NL][];
			for(int i = 0; i < in.ND; i++){
				for(int a = 0; a < in.NL; a++){
					V[i][a] = master.numVarArray( in.NP , 0 , 1 );				
				}
			}		
			Cost = new IloNumVar[in.Co][ ];
			for(int e = 0; e < in.Co; e++){
				Cost[e] = master.numVarArray( in.NP, 0 , Double.MAX_VALUE );
			}
			Alphas = new IloNumVar[in.Co][ ];		
			for(int e = 0; e < in.Co; e++){
				Alphas[e] = master.boolVarArray( in.NP );
			}
			/****************************** OBJECTIVE FUNCTION ******************************************/
			/********************************************************************************************/		
			IloLinearNumExpr objective = master.linearNumExpr( );
			for(int e = 0; e < in.Co; e++){
				for(int t = 0; t < in.NP; t++){
					objective.addTerm(1, Cost[e][t]);
				}
			}
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					for(int t = 0; t < in.NP; t++){
						if(in.Slinks[i][k] == true)
							objective.addTerm(in.SCost[i][k], q[i][k][t]);
					}
				}
			}		
			master.addMinimize(objective);
			
			/********************************** CONSTRAINTS **************************************/
			/*************************************************************************************/
			// C1: Customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint. constraints = in.NC*in.NP
			for(int k = 0; k < in.NC; k++){
				for(int t = 0; t < in.NP; t++){
					IloLinearNumExpr c1 = master.linearNumExpr( );
					for(int i = 0; i < in.ND; i++){
						if(in.Slinks[i][k] == true)
							c1.addTerm(1, q[i][k][t]);					
					}
					master.addGe(c1, in.Dem[k][t]);
				}
			}
			// C2: Arc capacity. Only open facilities can deliver goods to customers. constraints = in.ND*in.NC*in.NP 		  
			for(int i = 0; i < in.ND; i++){
				  for(int k = 0; k < in.NC; k++){
						if(in.Slinks[i][k] == true){  
							for(int t = 0; t < in.NP; t++){
								IloLinearNumExpr c2 = master.linearNumExpr( );
								for(int a = 0; a < in.maxNL[i][t]; a++){
									c2.addTerm(in.Dem[k][t], V[i][a][t]);
								}
								master.addLe(q[i][k][t], c2);
							}
						}
				  }
			}  	  
			// C3: Node Capacity. Enough Large Transportation capacity to carry depot's allocated demand. 		  
			for(int i = 0; i < in.ND; i++){
					for(int t = 0; t < in.NP; t++){
						IloLinearNumExpr c3 = master.linearNumExpr( );
						IloLinearNumExpr c3b = master.linearNumExpr( );
						for(int k = 0; k < in.NC; k++){
							if(in.Slinks[i][k] == true)																		
								c3.addTerm(1, q[i][k][t]);						
						}
						for(int a = 0; a < in.maxNL[i][t]; a++){
							c3b.addTerm(in.lvQ[a], V[i][a][t]);						
						}					
						master.addLe(c3, c3b);
					}
			}
			// C4: Companies costs per period
			for(int e = 0; e < in.Co; e++){
					for(int t = 0; t < in.NP; t++){								
						IloLinearNumExpr TCosts = master.linearNumExpr( );					
						for(int i = 0; i < in.ND; i++){
							if(in.owners[i] == e+1){								
								for(int a = 0; a < in.maxNL[i][t]; a++){
									TCosts.addTerm(in.LCost[i][a] + in.FCost[i], V[i][a][t]);
								}
							}
						}
						master.addGe( Cost[e][t], TCosts );
					}
			}
			/****************Relation between contracts and network constraints******************/
			// C5: mpc: If the contract is active it should be charged a lump sum. 		  
			for(int e = 0; e < in.Co; e++){		  
				for(int t = 0; t < in.NP; t++){
					  IloLinearNumExpr c7 = master.linearNumExpr( );
					  for(int n = 0; n < in.mct; n++){
						  if(t-n > -1){
							c7.addTerm( in.mpc[e][t], Alphas[e][t-n] );
						  }
					  }
					  master.addGe( Cost[e][t], c7 );
				 }
			 }
			 // C6: Consistency of depots. If there is one depot open the contract is Active.
			 for(int e = 0; e < in.Co; e++){
				  for(int i = 0; i < in.ND; i++){
					  if(in.owners[i] == e+1){
						for(int t = 0; t < in.NP; t++){	
							IloLinearNumExpr c6 = master.linearNumExpr( );
							IloLinearNumExpr Ve = master.linearNumExpr( );
							for(int a = 0; a < in.maxNL[i][t]; a++){
								Ve.addTerm(1, V[i][a][t]);
							}
							for(int n = 0; n < in.mct; n++){
								if(t-n > -1){
									c6.addTerm(1, Alphas[e][t-n]);
								}
							}
							master.addLe(Ve, c6);
						}
					  }
				  }
			 }		  		 		  
			 // C7: If the contract was not open in the last (MCT-1) periods, then it should not be currently ongoing  		  
			 for(int e = 0; e < in.Co; e++){
				  for(int t = 0; t < in.NP; t++){
					  IloLinearNumExpr c11 = master.linearNumExpr( );
						  	for(int n = 0; n < in.mct; n++){
						  		if(t-n > -1){
						  			c11.addTerm( 1, Alphas[e][t-n] );
						  		}
						  	}
					  master.addLe(c11, 1);
				  }
			  }			 
	
			  /************************** covering constraints *************************************/
			  // CC1: There should be least one open company linked with each customer
			  for(int k = 0; k < in.NC; k++){
				  for(int t = 0; t < in.NP; t++){
					  IloLinearNumExpr cc1 = master.linearNumExpr( );
					  for(int e = 0; e < in.Co; e++){
						  if ( in.companyCanDeliver(e, k) )
							  for(int n = 0; n < in.mct; n++){
								  if(t-n > -1) cc1.addTerm( 1, Alphas[e][t-n] );
							  }
					  }
					  master.addGe( cc1 , 1 ); 
				  }
			  }
			  // CC2: There should be at least one of each customer's linked depots open
			  for(int t = 0; t < in.NP; t++){
				  for(int k = 0; k < in.NC; k++){
					  IloLinearNumExpr cc2 = master.linearNumExpr( );
					  for(int i = 0; i < in.ND; i++){
						  if( in.Slinks[i][k] ){
							  for(int a = 0; a < in.maxNL[i][t]; a++){
								  cc2.addTerm( 1, V[i][a][t] );}
						  }
					  }
					  master.addGe( cc2 , 1 );
				  }
			  }
			  /************************************ CPLEX PARAMETERS*******************************/
			  /************************************************************************************/
			  master.setParam( IloCplex.Param.Benders.Strategy, 0 );
			  master.setParam( IloCplex.DoubleParam.TiLim, 3600 );
			  // master.setParam( IloCplex.IntParam.Threads, 4 );
			  master.setParam( IloCplex.IntParam.MIPDisplay, 0 );
			  // cplex.setParam( IloCplex.IntParam.ParallelMode, 0 );
			  master.setOut( null );

		} catch (IloException e) {
			// TODO: handle exception
			// return null; 
		}
		
	}

	
	
	
	/*********************************************
	 * set the Sub Problem (MILP)
	 * param []betas : solution of period=time
	 */
	public void solveSP_EP( int[] betas, int time, String mode ) {
		try {					
		sub = new IloCplex( );					
		/********************************** VARIABLES ***************************************/
		/************************************************************************************/	
		IloNumVar[][] q = new IloNumVar[in.ND][in.NC];
		for(int i = 0; i < in.ND; i++){
			for(int k = 0; k < in.NC; k++){
				if(in.Slinks[i][k] == true) 
					q[i][k] = sub.numVar( 0, Double.MAX_VALUE );				
			}
		}
		IloNumVar[][] V = new IloNumVar[in.ND][in.NL];
		for(int i = 0; i < in.ND; i++){
			for(int a = 0; a < in.maxNL[i][time]; a++){
				V[i][a] = sub.boolVar( );
			}
		}
		IloNumVar[] Cost = new IloNumVar[in.Co];
		for(int e = 0; e < in.Co; e++){
			Cost[e] = sub.numVar( 0 , Double.MAX_VALUE );
		}
		
		/********************************** CONSTRAINTS **************************************/
		/*************************************************************************************/		
		// C1: customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint.
		for(int k = 0; k < in.NC; k++){			
				IloLinearNumExpr c1 = sub.linearNumExpr( );
				for( int i = 0; i < in.ND; i++ ){
					if(in.Slinks[i][k] == true)
						c1.addTerm( 1, q[i][k] );					
				}
				sub.addGe( c1, in.Dem[k][time] );
		}		
		// C2: Only open facilities can deliver goods to customers. 		  
		for(int i = 0; i < in.ND; i++){
			  for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true){  		
							IloLinearNumExpr c2 = sub.linearNumExpr( );
							for(int a = 0; a < in.maxNL[i][time]; a++){	
								c2.addTerm( in.Dem[k][time], V[i][a] );
							}
							sub.addLe(q[i][k], c2);																				
					}				
			  }
		  }
		  //  C3: Enough Large Transportation capacity to carry depot's allocated demand. 		  
		  for(int i = 0; i < in.ND; i++){				
				IloLinearNumExpr c3 = sub.linearNumExpr( );
				IloLinearNumExpr c3b = sub.linearNumExpr( );
				for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true)																		
						c3.addTerm(1, q[i][k]);						
				}
				for(int a = 0; a < in.maxNL[i][time]; a++){
					c3b.addTerm(in.lvQ[a], V[i][a]);						
				}					
				sub.addLe(c3, c3b);				
		  }
		  // C4: Companies costs per period
		  for(int e = 0; e < in.Co; e++){																	
				IloLinearNumExpr TCosts = sub.linearNumExpr( );					
				for(int i = 0; i < in.ND; i++){
					if(in.owners[i] == e+1){										
						for(int a = 0; a < in.maxNL[i][time]; a++){
							TCosts.addTerm(in.FCost[i] + in.LCost[i][a], V[i][a]);
						}
					}
				}							
				sub.addGe(Cost[e], TCosts);				
		  }		  
		  // C5: Consistency of depots. If there is one depot open the contract is Active.		  
		  for(int e = 0; e < in.Co; e++){
			  for(int i = 0; i < in.ND; i++){
				  if(in.owners[i] == e+1){
						IloLinearNumExpr Ve = sub.linearNumExpr( );
						for(int a = 0; a < in.maxNL[i][time]; a++){
							Ve.addTerm( 1, V[i][a] );
						}						
						sub.addLe( Ve, betas[e] );
				  }
			  }			  
		  }
		  // C6: in.mpc: If the contract is active it should be charged an denominated amount. 		  
		  for(int e = 0; e < in.Co; e++){					 
			  sub.addGe( Cost[e], betas[e] * in.mpc[e][time] );
		  }		  
		  
		  /************************************ OBJECTIVE FUNCTION ****************************************/
		  /************************************************************************************************/
		  IloLinearNumExpr objective = sub.linearNumExpr( );
		  for(int e = 0; e < in.Co; e++){
					objective.addTerm( 1,Cost[e]);				
	      }
		  for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					if( in.Slinks[i][k] == true )
						objective.addTerm(in.SCost[i][k], q[i][k]);					
				}
		  }		

		 /************************************ CPLEX PARAMETERS*******************************/
	     /************************************************************************************/
		  sub.setOut( null );
		  sub.setParam( IloCplex.IntParam.MIPDisplay, 0 );				
		  //sub.setParam( IloCplex.IntParam.Threads, 4 );
	      //sub.setParam( IloCplex.DoubleParam.TiLim, 200 );
		  sub.addMinimize(objective);		
		  if( sub.solve( ) ) {
			  if(mode.equals("collect")) {
			  for(int e = 0; e < in.Co; e++){ 
			  costCarrier[e][time] = in.round( sub.getValue( Cost[e] ), 2 );
			  for(int i = 0; i < in.ND; i++){	 
				  for( int a = 0; a < in.maxNL[i][time]; a++ ){
					  capLevels_V[i][a][time] = (int) in.round( sub.getValue( V[i][a] ), 2 );	
				  }
				  for(int k = 0; k < in.NC; k++) {
					  if( in.Slinks[i][k] )
					  allocDem_q[i][k][time] = sub.getValue( q[i][k] );
				  }
			  }}
			  }
		  }
		  
		  
		} catch (IloException e) {
			// TODO: handle exception
			// return null; 
		}
		
	}
	
	
	
	
	
	/*********************************************
	 * Add the callback to the RMP : follow the steps during the RMP process. 
	 * 1. Check the solution value for comparison
	 * 2. if potential improvement on Z_UB --> retrieve alphas, solve the SP. 
	 * 3. if a best solution is found -> update BF values.
	 * 4. In any case add the combinatorial cut.
	 */
	public void setCallback( ) throws IloException {
		master.use( new combCutCallback( ) );						
	}			
	
		
	
	private class combCutCallback extends IloCplex.LazyConstraintCallback{		
		@Override
		protected void main( ) throws IloException {			 
			 myCallback( );
		}			 
		@SuppressWarnings("static-access")		
		protected void myCallback( ) throws IloException {	
			 cbIter++;
			 double nodeValue = getValue( master.getObjective( ).getExpr( ) );	
			 //System.err.println( "CB "+ cbIter +" : "+ nodeValue + " -- UB "+ Z_UB  );
			 // advisable lower bound  ==  best found value of the RMP  // update the UB on the RMP. 
			 if( nodeValue < advLB )
				 advLB = nodeValue;
			 // solution values for contracts : 
			 tempBetas = new int[in.Co][in.NP];
			 for( int e = 0; e < in.Co; e++ ){ 
				 for(int t = 0; t < in.NP; t++){ 
			 		tempAlphas[e][t] = (int) Math.round( getValue( Alphas[e][t] ) ) ;
			 		if( tempAlphas[e][t] == 1 ){
						  for(int n = 0; n < in.mct; n++){
							  if( t+n < in.NP ) {
								  tempBetas[e][t+n] = 1;
							  }
						  }
					}
			 } }
			 // add( combinatorialCut( ) );
			 // CASE 1. The Node Value could be over the UB -> Delete that shit !!	
			 //if( nodeValue > Z_UB ) {
			    //add( combinatorialCut( ) );
			 	//System.err.println ( "infeasible solution " );
			    //System.err.println(); printTempBetas( );
			 //}
		 	 // CASE 2. The Node Value is lower than best UB;
			 // Retrieve the feasible integer solution : The values of Candidate Alphas found at the node.	
			 if ( nodeValue <= Z_UB ) {
			 //else{
				 //printTempAlphas( );  System.err.println(); printTempBetas( );
				 double MDPC_value = solveSP_EP( );
					 if( MDPC_value < Z_UB ) { 			 // IF NEW UB IS FOUND  -----  !!!
						// printTempBetas( );
						//System.err.println ( "NEW UB :"+ MDPC_value );
						Z_UB = MDPC_value;	
						// <<<<<<< ------------------- Copy decision variables values ------------------ >>>>>> //
						for(int e = 0; e < in.Co; e++){ 
					    for(int t = 0; t < in.NP; t++){					   
							   alphas[e][t] = tempAlphas[e][t];
							   betas[e][t] = tempBetas[e][t];
				   	  	}}
						// Every solution with value above is not feasible. 
						// master.setParam(  IloCplex.DoubleParam.CutUp, Z_UB ); -> useless
						add( master.le( master.getObjective( ).getExpr( ), Z_UB ) ); // --> costo del RMP menor al UB (MDPC)
					 }	
				 add( combinatorialCut( ) );
				 //System.err.println( "------------------------------ "+ master.getStatus( ).toString( ));
				 
				 //if( master.getStatus( ).equals("feasible") )
				 //Z_LB = master.getBestObjValue( )
			 }
		}
	}
	
	
	
	/****************************************************************************
	 * @return
	 * @throws IloException
	 */
	public double solveSP_EP( ) throws IloException {
		spIter++;
		double objValue = 0;		
		double[] obj_Value = new double[in.NP];
		double startSP = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
		for(int t = 0; t < in.NP; t++){
			int[ ] tBetas = new int[in.Co];
			for( int e = 0; e < in.Co; e++ ) tBetas[e] = tempBetas[e][t];
			// Turn binary solution at t, to unique decimal number.
			int dec_Number = decNumber( tBetas ); // 0 --> LOG_MAX.
			//System.out.print ( "location of solution at period "+t +" : "+ dec_Number);
			boolean tested = checkBetasAtT( dec_Number, t ); // already tested or not
			//System.out.println ( " - tested: "+ tested + "- value: "+ betaStorage[t][dec_Number]);
			if( tested ) { // if already tested, find the obj. value for the current solution (period t)
				obj_Value[t] = betaStorage[t][dec_Number];
				objValue += obj_Value[t];
			}else {
				solveSP_EP( tBetas , t, "" ); // solve the single-period Sub Problem for the current solution at period t
				obj_Value[t] = sub.getObjValue( );
				spIter_ep[t]++;
				//System.out.println ( "value: "+ obj_Value[t] );
				objValue += obj_Value[t];
				betaStorage[t][dec_Number] = obj_Value[t];
			}
		}
		double finishSP = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
		//System.err.println( "SP "+ spIter +" :"+ objValue + "$; " + " Time :"+ ( finishSP - startSP ) );	
		return objValue;
	}
	
	
	
	
	public IloRange combinatorialCut( ) throws IloException {
		IloLinearNumExpr cc = master.linearNumExpr( );
		 int unos = 0;
		 for(int e = 0; e < in.Co; e++){
			for(int t = 0; t < in.NP; t++){
				if( tempAlphas[e][t] == 0 ){ 
					cc.addTerm( 1,  Alphas[e][t] );					
				} 
				else if( tempAlphas[e][t] == 1 ){
					cc.addTerm( -1, Alphas[e][t] );
					unos++; 
					t += in.mct-1;
				}
			}			
		 }
		 return  master.ge( cc, 1-unos );
	}
	
	
	
	public IloRange combinatorialCutComplete( ) throws IloException {
		IloLinearNumExpr cc = master.linearNumExpr( );
		 int unos = 0;
		 for(int e = 0; e < in.Co; e++){
			for(int t = 0; t < in.NP; t++){
				if( tempAlphas[e][t] == 0 ){ 
					cc.addTerm( 1,  Alphas[e][t] );					
				} 
				else if( tempAlphas[e][t] == 1 ){
					cc.addTerm( -1, Alphas[e][t] );
					unos++; 
					t += in.mct-1;
				}
			}			
		 }
		 return  master.ge( cc, 1-unos );
	}
	
	

	public int decNumber(int[] tBetas) {
		int logN = 0;
		int [] dec = { 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048 }; // 12 carriers max.
		for(int i = 0; i < tBetas.length; i++) {
			logN += ( tBetas[i] * dec[i] );
		}
		return logN;
	}

	
	
	public boolean checkBetasAtT(int log_number, int t) {
		if( betaStorage[t][log_number] > 0 )
			return true;
		else
			return false;
	}
	

	
	public void printTempBetas( ){
		for(int e = 0; e < in.Co; e++){	 
			System.err.print( " Company " + e + " : " ); 
		 	for(int t = 0; t < in.NP; t++) {
		 		System.err.print(  tempBetas[e][t]    + " | ");
		 	}	
		 	System.err.println( );
		}
	}
	
	
	
	public void printTempAlphas( ){
		for(int e = 0; e < in.Co; e++){	 
			System.err.print( " Company " + e + " : " ); 
		 	for(int t = 0; t < in.NP; t++) {
		 		System.err.print(  tempAlphas[e][t]    + " | ");
		 	}	
		 	System.err.println( );
		}
	}

	
	
	
	/******************************** PRINT OUTPUT METHODS ***********************************/
	/*****************************************************************************************/
	public void printBasicSolutionValues( Instance in , String st ){
		  System.out.println( ) ;
		  System.out.println( ">>>>>>>>>>>>>>>>>>>>>>>>>>> Solution method: " +st+ " <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<" ) ;
		  System.out.println( ) ;
		  String format = "%-15s %12s"; 
		  System.out.format(format,  "Comp. Time : ",  sol_time +"s ("+ setup_time + "s)" ) ; System.out.println( );
		  System.out.format(format, "Best Value : ", in.round( Z_UB ,2 )  ) ; System.out.println( );
		  System.out.format(format, "LB : ", in.round( Z_LB ,2 ) ) ;   System.out.println( );
		  System.out.format(format, "Adv. LB : ", in.round( advLB ,2 ) ) ;  System.out.println( );
		  System.out.format(format, "GAP : ", in.round( Z_gap ,5 ) ) ;  System.out.println( );
		  System.out.format(format, "Adv. GAP : ", in.round( advGap ,2 ) ) ;System.out.println( );
		  System.out.println( );
		  printBetas( in );
		  System.out.println( );
	}
	
	
	
	
}
