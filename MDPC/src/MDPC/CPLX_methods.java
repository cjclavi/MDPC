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
/**
 * Clase con metodos para resolver una instancia del problema MDPC
 * CPBC: Resuelve el modelo MDPC directamente por medio de CPLEX(Standard)
 */
public class CPLX_methods extends Solution_method {
	/***********************************************************************************************
	 * ATTRIBUTES
	 ************************************************************************************************/

	
	/***********************************************************************************************
	 * CONSTRUCTOR
	 * Crea una instancia de la clase CPLX Methods que puede llamar o correr los metodos de solucion
	 **************************************************/
		public CPLX_methods( Instance in ){
			super ( in );
		}
		
		
	/*******************************************************************************************************************************
	 * METHODS
	 ******************************************************************************************************************/
	
	 	
	public void solveMDPC( Instance in , int st ){
		if(in.mct == 1) solveMDPC_1CT( in );
		else if ( in.mct > 1 && in.mct < in.NP) solveMDPC_XCT( in , st );
		else if ( in.mct == in.NP ) solveMDPC_FCT( in , st );
		
		/*********************** CPLEX OUTPUT **************************/
		String method = "";
		if(st == 0) method = "CPLEX B&C"; 
		if(st == 3) method = "CPLEX Benders";
		if(in.mpcParam == 0) printOutput_2( in, method );
		else printOutput_1( in, method );
	}
	
		

	/******************************************
	 * Complete Model : Main Problem (Integer V_{i,a})
	 * st: 0, Cplex B&C; 3, Cplex Benders.
	 *******************/
	public void solveMDPC_XCT( Instance in , int st ){	
		resetValues( in );
		long start1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		try {					
			IloCplex cplex = new IloCplex( );
			// IloCplex.LongAnnotation benders = cplex.newLongAnnotation("cpxBendersPartition");		
			
			/********************************** VARIABLES **************************************/
			/***********************************************************************************/												
			IloNumVar[][][] q = new IloNumVar[in.ND][in.NC][ ];		
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true)
						q[i][k] = cplex.numVarArray( in.NP, 0, Double.MAX_VALUE );
				}
			}
			IloNumVar[][][] V = new IloNumVar[in.ND][in.NL][];
			for(int i = 0; i < in.ND; i++){
				for(int a = 0; a < in.NL; a++){
					V[i][a] = cplex.boolVarArray( in.NP );				
				}
			}		
			IloNumVar[][] Cost = new IloNumVar[in.Co][ ];
			for(int e = 0; e < in.Co; e++){
				Cost[e] = cplex.numVarArray( in.NP, 0 , Double.MAX_VALUE );
			}
			IloNumVar[][] Alpha = new IloNumVar[in.Co][ ];		
			for(int e = 0; e < in.Co; e++){
				Alpha[e] = cplex.boolVarArray( in.NP );
			}
			
			/****************************** OBJECTIVE FUNCTION ******************************************/
			/********************************************************************************************/		
			IloLinearNumExpr objective = cplex.linearNumExpr( );
			for(int e = 0; e < in.Co; e++){
				for(int t = 0; t < in.NP; t++){
					objective.addTerm(1, Cost[e][t]);
				}
			}
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					for(int t = 0; t < in.NP; t++){
						if(in.Slinks[i][k] == true)
							objective.addTerm(in.SCost[i][k], q[i][k][t]) ;
					}
				}
			}		
			cplex.addMinimize(objective);
			
			/********************************** CONSTRAINTS **************************************/
			/*************************************************************************************/
			// C1: Customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint. constraints = in.NC*in.NP
			for(int k = 0; k < in.NC; k++){
				for(int t = 0; t < in.NP; t++){
					IloLinearNumExpr c1 = cplex.linearNumExpr( );
					for(int i = 0; i < in.ND; i++){
						if(in.Slinks[i][k] == true)
							c1.addTerm( 1, q[i][k][t] );					
					}
					cplex.addGe(c1, in.Dem[k][t]);
				}
			}
			// C2: Arc capacity. Only open facilities can deliver goods to customers. constraints = in.ND*in.NC*in.NP 		  
			for(int i = 0; i < in.ND; i++){
				  for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true){  
						for(int t = 0; t < in.NP; t++){
							IloLinearNumExpr c2 = cplex.linearNumExpr( );
							for(int a = 0; a < in.maxNL[i][t]; a++){
								c2.addTerm( in.Dem[k][t], V[i][a][t] );
							}
							cplex.addLe(q[i][k][t], c2);
						}
					}
				  }
			}  	  
			// C3: Node Capacity. Enough Large Transportation capacity to carry depot's allocated demand. 		  
			for(int i = 0; i < in.ND; i++){
					for(int t = 0; t < in.NP; t++){
						IloLinearNumExpr c3 = cplex.linearNumExpr( );
						IloLinearNumExpr c3b = cplex.linearNumExpr( );
						for(int k = 0; k < in.NC; k++){
							if(in.Slinks[i][k] == true)																		
								c3.addTerm(1, q[i][k][t]);						
						}
						for(int a = 0; a < in.maxNL[i][t]; a++){
							c3b.addTerm(in.lvQ[a], V[i][a][t]);						
						}					
						cplex.addLe(c3, c3b);
					}
			}
			// C4: Companies costs per period
			for(int e = 0; e < in.Co; e++){
					for(int t = 0; t < in.NP; t++){								
						IloLinearNumExpr TCosts = cplex.linearNumExpr( );					
						for(int i = 0; i < in.ND; i++){
							if(in.owners[i] == e+1){								
								for( int a = 0; a < in.maxNL[i][t]; a++ ){
									TCosts.addTerm( in.LCost[i][a] + in.FCost[i], V[i][a][t] );
								}
							}
						}
						cplex.addGe(Cost[e][t], TCosts);
					}
			}
			// Relation between contracts and network constraints******************/
			// C5: mpc: If the contract is active it should be charged a lump sum. 		  
			for(int e = 0; e < in.Co; e++){		  
				for(int t = 0; t < in.NP; t++){
					  IloLinearNumExpr c7 = cplex.linearNumExpr( );
					  for(int n = 0; n < in.mct; n++){
						  if(t-n > -1){
							c7.addTerm( in.mpc[e][t], Alpha[e][t-n] );
						  }
					  }
					  cplex.addGe(Cost[e][t], c7);
				 }
			 }
			 // C6: Consistency of depots. If there is one depot open the contract is Active.
			 for(int e = 0; e < in.Co; e++){
				  for(int i = 0; i < in.ND; i++){
					  if(in.owners[i] == e+1){
						for(int t = 0; t < in.NP; t++){	
							IloLinearNumExpr c6 = cplex.linearNumExpr( );
							IloLinearNumExpr Ve = cplex.linearNumExpr( );
							for(int a = 0; a < in.maxNL[i][t]; a++){
								Ve.addTerm(1, V[i][a][t]);
							}
							for(int n = 0; n < in.mct; n++){
								if(t-n > -1){
									//c6.addTerm(in.mpc[e][t], Alpha[e][t-n]);
									c6.addTerm(1, Alpha[e][t-n]);
								}
							}
							cplex.addLe(Ve, c6);
						}
					  }
				  }
			 }		  		 		  
			 // C7: If the contract was not open in the last (MCT-1) periods, then it should not be currently ongoing  		  
			 for(int e = 0; e < in.Co; e++){
				  for(int t = 0; t < in.NP; t++){
					  IloLinearNumExpr c11 = cplex.linearNumExpr( );
						  	for(int n = 0; n < in.mct; n++){
						  		if(t-n > -1){
						  			c11.addTerm( 1, Alpha[e][t-n] );
						  		}
						  	}
					  cplex.addLe(c11, 1);
				  }
			  }			 

			  /************************** covering constraints *************************************/
			  // CC1: There should be least one open company linked with each customer
			  for(int k = 0; k < in.NC; k++){
					  for(int t = 0; t < in.NP; t++){
						  IloLinearNumExpr cc1 = cplex.linearNumExpr( );
						  for(int e = 0; e < in.Co; e++){
							  if ( in.companyCanDeliver(e, k) )
								  for(int n = 0; n < in.mct; n++){
									  if(t-n > -1) cc1.addTerm( 1, Alpha[e][t-n] );
								  }
						  }
						  cplex.addGe( cc1 , 1 ); 
					  }
			  }
			  // CC2: There should be at least one of each customer's linked depots open
			  for(int t = 0; t < in.NP; t++){
				  for(int k = 0; k < in.NC; k++){
					  IloLinearNumExpr cc2 = cplex.linearNumExpr( );
					  for(int i = 0; i < in.ND; i++){
						  if( in.Slinks[i][k] ){
							  for(int a = 0; a < in.maxNL[i][t]; a++)
								  cc2.addTerm( 1, V[i][a][t] );
						  }
					  }
					  cplex.addGe( cc2 , 1 );
				  }
			  }
			  /************************************ CPLEX PARAMETERS*******************************/
			  /************************************************************************************/
			  cplex.setOut(null);
			  cplex.setParam( IloCplex.Param.Benders.Strategy, st );
			  cplex.setParam( IloCplex.DoubleParam.TiLim, 3600 );
			  //cplex.setParam( IloCplex.IntParam.Threads, 4 );
			  cplex.setParam( IloCplex.IntParam.MIPDisplay, 3 );
			  //cplex.setParam( IloCplex.IntParam.ParallelMode, 0 );
			  //cplex.setParam( IloCplex.IntParam.Threads, 1 );
			  long finish1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
			  long start2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
			  
			  if( cplex.solve( ) ) { 
			  long finish2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
			  //--------------- Copy decision variables values ------>>>>>>
			  for(int e = 0; e < in.Co; e++){ 
			  for(int t = 0; t < in.NP; t++){						   
				  alphas[e][t] = (int) in.round( cplex.getValue( Alpha[e][t] ), 2 ); 
				  if( alphas[e][t] == 1 ){
					  for(int n = 0; n < in.mct; n++){
						  if( t+n < in.NP ) betas[e][t+n] = 1;
					  }
				  }
				  costCarrier[e][t] = in.round( cplex.getValue( Cost[e][t] ), 2 );
				  for(int i = 0; i < in.ND; i++){	 
					  for( int a = 0; a < in.maxNL[i][t]; a++ ){
						  capLevels_V[i][a][t] = (int) in.round( cplex.getValue( V[i][a][t] ), 2 );	
					  }
					  for(int k = 0; k < in.NC; k++) {
						  if( in.Slinks[i][k] )
						  allocDem_q[i][k][t] = cplex.getValue( q[i][k][t] );
					  }
				  }
			  	}}
			  	//--------------- Copy (best found) solution values ------>>>>>>
				Z_UB = cplex.getObjValue( );
				Z_LB = cplex.getBestObjValue( );
				Z_gap = cplex.getMIPRelativeGap( );
				sol_time = finish2 - start2;
				setup_time = finish1 - start1;
			  }
			  cplex.end( );
		}
		catch( IloException e ){
			e.printStackTrace( );
		}
		
	}
	
	
	
	
	/****************************************************************
	 * Solve the MDPC problem period per period for mct = 1  (SDPC)
	 * @param in instance
	 *********************************/
	public void solveMDPC_1CT( Instance in ) {
		resetValues( in );
		long start1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		for(int t = 0; t < in.NP; t++) 
			solveMDPC_1P( in, t ); 
		long finish1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		sol_time = finish1 - start1;		
	}
	
	
	

	/****************************************************************
	 * Solve the MDPC problem for one period  (SDPC)
	 * @param time
	 *********************************/
	private void solveMDPC_1P( Instance in, int time ){
		
		try {					
			IloCplex cplex = new IloCplex( );						
			/********************************** VARIABLES ***************************************/
			/************************************************************************************/	
			IloNumVar[][] q = new IloNumVar[in.ND][in.NC];
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true) 
						q[i][k] = cplex.numVar( 0, Double.MAX_VALUE );				
				}
			}
			IloNumVar[][] V = new IloNumVar[in.ND][in.NL];
			for(int i = 0; i < in.ND; i++){
				for(int a = 0; a < in.NL; a++){
					V[i][a] = cplex.boolVar( );
				}
			}
			IloNumVar[] Cost = new IloNumVar[in.Co];
			for(int e = 0; e < in.Co; e++){
				Cost[e] = cplex.numVar( 0 , Double.MAX_VALUE );
			}
			IloNumVar[] Alpha = new IloNumVar[in.Co];		
			for(int e = 0; e < in.Co; e++){
				Alpha[e] = cplex.boolVar( );
			}
			
			
			/********************************** CONSTRAINTS **************************************/
			/*************************************************************************************/		
			// C1: customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint.
			for(int k = 0; k < in.NC; k++){			
					IloLinearNumExpr c1 = cplex.linearNumExpr( );
					for( int i = 0; i < in.ND; i++ ){
						if(in.Slinks[i][k] == true)
							c1.addTerm( 1, q[i][k] );					
					}
					cplex.addGe( c1, in.Dem[k][time] );
			}		
			// C2: Only open facilities can deliver goods to customers. 		  
			for(int i = 0; i < in.ND; i++){
				  for(int k = 0; k < in.NC; k++){
						if(in.Slinks[i][k] == true){  		
							IloLinearNumExpr c2 = cplex.linearNumExpr( );
							for(int a = 0; a < in.NL; a++){	
								c2.addTerm( in.Dem[k][time], V[i][a] );
							}
							cplex.addLe(q[i][k], c2);																				
						}				
				  }
			  }
			  //  C3: Enough Large Transportation capacity to carry depot's allocated demand. 		  
			  for(int i = 0; i < in.ND; i++){				
					IloLinearNumExpr c3 = cplex.linearNumExpr( );
					IloLinearNumExpr c3b = cplex.linearNumExpr( );
					for(int k = 0; k < in.NC; k++){
						if(in.Slinks[i][k] == true)																		
							c3.addTerm(1, q[i][k]);						
					}
					for(int a = 0; a < in.NL; a++){
						c3b.addTerm(in.lvQ[a], V[i][a]);						
					}					
					cplex.addLe(c3, c3b);				
			  }
			  // C4: Companies costs per period
			  for(int e = 0; e < in.Co; e++){																	
					IloLinearNumExpr TCosts = cplex.linearNumExpr( );					
					for(int i = 0; i < in.ND; i++){
						if(in.owners[i] == e+1){										
							for(int a = 0; a < in.NL; a++){
								TCosts.addTerm(in.FCost[i] + in.LCost[i][a], V[i][a]);
							}
						}
					}							
					cplex.addGe(Cost[e], TCosts);				
			  }		  
			  // C5: in.mpc: If the contract is active it should be charged an denominated amount. 		  
			  for(int e = 0; e < in.Co; e++){	
				  IloLinearNumExpr mpc = cplex.linearNumExpr( );
				  mpc.addTerm( in.mpc[e][time], Alpha[e] );
				  cplex.addGe( Cost[e], mpc );
			  }		  
			  
			  // C6: Consistency of depots. If there is one depot open the contract is Active.		  
			  for(int e = 0; e < in.Co; e++){
				  for(int i = 0; i < in.ND; i++){
					  if(in.owners[i] == e+1){
						    IloLinearNumExpr Ve = cplex.linearNumExpr( );
							for(int a = 0; a < in.NL; a++){
								Ve.addTerm( 1, V[i][a] );
							}					
							cplex.addLe( Ve, Alpha[e] );
					  }
				  }		
			  }
			  
			  /************************************ OBJECTIVE FUNCTION ****************************************/
			  /************************************************************************************************/
			  IloLinearNumExpr objective = cplex.linearNumExpr( );
			  for(int e = 0; e < in.Co; e++){
						objective.addTerm(1,Cost[e]);				
		      }
			  for(int i = 0; i < in.ND; i++){
					for(int k = 0; k < in.NC; k++){
						if( in.Slinks[i][k] == true )
						objective.addTerm( in.SCost[i][k], q[i][k] );					
					}
			  }		
			  cplex.addMinimize(objective);		
			    
			/****************************************** CPLEX PARAMETERS ************************************/
		    /************************************************************************************************/
			  cplex.setOut( null );
			  cplex.setParam( IloCplex.IntParam.MIPDisplay, 0 );				
			  //sub.setParam( IloCplex.IntParam.Threads, 4 );
		      //sub.setParam( IloCplex.DoubleParam.TiLim, 200 );
			  
			  if( cplex.solve( ) ) {
				  for(int e = 0; e < in.Co; e++){ 				   
						  betas[e][time] = (int) in.round( cplex.getValue( Alpha[e] ), 2  );
						  costCarrier[e][time] = in.round( cplex.getValue( Cost[e] ), 2 );
						  for(int i = 0; i < in.ND; i++){	 
							  for( int a = 0; a < in.maxNL[i][time]; a++ ){
								  capLevels_V[i][a][time] = (int) in.round( cplex.getValue( V[i][a] ), 2 );	
							  }
							  for(int k = 0; k < in.NC; k++) {
								  if( in.Slinks[i][k] )
								  allocDem_q[i][k][time] = cplex.getValue( q[i][k] );
							  }
						  }
				  }
				  Z_UB += cplex.getObjValue( );
			  }
			} catch (IloException e) {
				// TODO: handle exception
				// return null; 
			}
	
		
	}	
	
	

	
	/****************************************************************
	 * Solve the MDPC problem for full-horizon contracts. 
	 *********************************/
	public void solveMDPC_FCT(Instance in, int st) {
		resetValues( in );
		long start1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		try {					
			IloCplex cplex = new IloCplex( );
			// IloCplex.LongAnnotation benders = cplex.newLongAnnotation("cpxBendersPartition");		
			/********************************** VARIABLES **************************************/
			/***********************************************************************************/												
			IloNumVar[][][] q = new IloNumVar[in.ND][in.NC][ ];		
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true)
						q[i][k] = cplex.numVarArray( in.NP, 0, Double.MAX_VALUE );
				}
			}
			IloNumVar[][][] V = new IloNumVar[in.ND][in.NL][];
			for(int i = 0; i < in.ND; i++){
				for(int a = 0; a < in.NL; a++){
					V[i][a] = cplex.boolVarArray( in.NP );				
				}
			}		
			IloNumVar[][] Cost = new IloNumVar[in.Co][ ];
			for(int e = 0; e < in.Co; e++){
				Cost[e] = cplex.numVarArray( in.NP, 0 , Double.MAX_VALUE );
			}
			IloNumVar[] Alpha = new IloNumVar[in.Co];		
			for(int e = 0; e < in.Co; e++) {
				Alpha[e] = cplex.boolVar( );
			}
			
			/****************************** OBJECTIVE FUNCTION ******************************************/
			/********************************************************************************************/		
			IloLinearNumExpr objective = cplex.linearNumExpr( );
			for(int e = 0; e < in.Co; e++){
				for(int t = 0; t < in.NP; t++){
					objective.addTerm( 1, Cost[e][t] );
				}
			}
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					for(int t = 0; t < in.NP; t++){
						if(in.Slinks[i][k] == true)
							objective.addTerm(in.SCost[i][k], q[i][k][t]) ;
					}
				}
			}		
			cplex.addMinimize(objective);
			
			/********************************** CONSTRAINTS **************************************/
			/*************************************************************************************/
			// C1: Customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint. constraints = in.NC*in.NP
			for(int k = 0; k < in.NC; k++){
				for(int t = 0; t < in.NP; t++){
					IloLinearNumExpr c1 = cplex.linearNumExpr( );
					for(int i = 0; i < in.ND; i++){
						if(in.Slinks[i][k] == true)
							c1.addTerm( 1, q[i][k][t] );					
					}
					cplex.addGe(c1, in.Dem[k][t]);
				}
			}
			// C2: Arc capacity. Only open facilities can deliver goods to customers. constraints = in.ND*in.NC*in.NP 		  
			for(int i = 0; i < in.ND; i++){
				  for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true){  
						for(int t = 0; t < in.NP; t++){
							IloLinearNumExpr c2 = cplex.linearNumExpr( );
							for(int a = 0; a < in.maxNL[i][t]; a++){
								c2.addTerm( in.Dem[k][t], V[i][a][t] );
							}
							cplex.addLe(q[i][k][t], c2);
						}
					}
				  }
			}  	  
			// C3: Node Capacity. Enough Large Transportation capacity to carry depot's allocated demand. 		  
			for(int i = 0; i < in.ND; i++){
					for(int t = 0; t < in.NP; t++){
						IloLinearNumExpr c3 = cplex.linearNumExpr( );
						IloLinearNumExpr c3b = cplex.linearNumExpr( );
						for(int k = 0; k < in.NC; k++){
							if(in.Slinks[i][k] == true)																		
								c3.addTerm(1, q[i][k][t]);						
						}
						for(int a = 0; a < in.maxNL[i][t]; a++){
							c3b.addTerm(in.lvQ[a], V[i][a][t]);						
						}					
						cplex.addLe(c3, c3b);
					}
			}
			// C4: Companies costs per period
			for(int e = 0; e < in.Co; e++){
					for(int t = 0; t < in.NP; t++){								
						IloLinearNumExpr TCosts = cplex.linearNumExpr( );					
						for(int i = 0; i < in.ND; i++){
							if(in.owners[i] == e+1){								
								for( int a = 0; a < in.maxNL[i][t]; a++ ){
									TCosts.addTerm( in.LCost[i][a] + in.FCost[i], V[i][a][t] );
								}
							}
						}
						cplex.addGe(Cost[e][t], TCosts);
					}
			}
			// Relation between contracts and network constraints******************/
			// C5: mpc: If the contract is active it should be charged a lump sum. 		  
			for(int e = 0; e < in.Co; e++){		  
				for(int t = 0; t < in.NP; t++){
					  IloLinearNumExpr c7 = cplex.linearNumExpr( );	  
					  c7.addTerm( in.mpc[e][t], Alpha[e] );	  
					  cplex.addGe( Cost[e][t], c7 );
				 }
			 }
			 // C6: Consistency of depots. If there is one depot open the contract is Active.
			 for(int e = 0; e < in.Co; e++){
				  for(int i = 0; i < in.ND; i++){
					  if(in.owners[i] == e+1){
							for(int t = 0; t < in.NP; t++){	
								IloLinearNumExpr c6 = cplex.linearNumExpr( );
								IloLinearNumExpr Ve = cplex.linearNumExpr( );
								for(int a = 0; a < in.maxNL[i][t]; a++){
									Ve.addTerm( 1, V[i][a][t] );
								}
								//c6.addTerm( in.mpc[e][t], Alpha[e] );
								c6.addTerm( 1, Alpha[e] );
								cplex.addLe( Ve, c6 );
							}
					  }
				  }
			 }		  		 		  
			 // C7: selection of carriers along the planning horizon	  
			 for(int e = 0; e < in.Co; e++){
					  cplex.addLe(Alpha[e], 1);  
			  }			 

			  /************************** covering constraints *************************************/
			  // CC1: There should be least one open company linked with each customer
			 for(int k = 0; k < in.NC; k++){
					  IloLinearNumExpr cc1 = cplex.linearNumExpr( );
					  for(int e = 0; e < in.Co; e++){
						  if ( in.companyCanDeliver(e, k) )
							  cc1.addTerm( 1, Alpha[e] );
					  }
					  cplex.addGe( cc1 , 1 ); 
			  }
			  // CC2: There should be at least one of each customer's linked depots open
			  for(int t = 0; t < in.NP; t++){
				  for(int k = 0; k < in.NC; k++){
					  IloLinearNumExpr cc2 = cplex.linearNumExpr( );
					  for(int i = 0; i < in.ND; i++){
						  if( in.Slinks[i][k] ){
							  for(int a = 0; a < in.maxNL[i][t]; a++){
								  cc2.addTerm( 1, V[i][a][t] );}
						  }
					  }
					  cplex.addGe( cc2 , 1 );
				  }
			  }
			  /************************************ CPLEX PARAMETERS*******************************/
			  /************************************************************************************/
			  cplex.setOut(null);
			  cplex.setParam( IloCplex.Param.Benders.Strategy, st );
			  cplex.setParam( IloCplex.DoubleParam.TiLim, 3600 );
			  //cplex.setParam( IloCplex.IntParam.Threads, 4 );
			  cplex.setParam( IloCplex.IntParam.MIPDisplay, 3 );
			  //cplex.setParam( IloCplex.IntParam.ParallelMode, 0 );
			  //cplex.setParam( IloCplex.IntParam.Threads, 1 );
			  long finish1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
			  long start2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
			  
			  if( cplex.solve( ) ) { 
			  long finish2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
			  //--------------- Copy decision variables values ------>>>>>>
			  for(int e = 0; e < in.Co; e++){ 
			  for(int t = 0; t < in.NP; t++){							   
				  alphas[e][0] = (int) in.round( cplex.getValue( Alpha[e] ), 2 ); 
				  if( alphas[e][0] == 1 ){
						  betas[e][t] = 1;
				  }
				  costCarrier[e][t] = in.round( cplex.getValue( Cost[e][t] ), 2 );
				  for(int i = 0; i < in.ND; i++){	 
					  for( int a = 0; a < in.maxNL[i][t]; a++ ){
						  capLevels_V[i][a][t] = (int) in.round( cplex.getValue( V[i][a][t] ), 2 );	
					  }
					  for(int k = 0; k < in.NC; k++) {
						  if( in.Slinks[i][k] )
						  allocDem_q[i][k][t] = cplex.getValue( q[i][k][t] );
					  }
				  }
			  	}}
			  	//--------------- Copy (best found) solution values ------>>>>>>
				Z_UB = cplex.getObjValue( );
				Z_LB = cplex.getBestObjValue( );
				Z_gap = cplex.getMIPRelativeGap( );
				sol_time = finish2 - start2;
				setup_time = finish1 - start1;
			  }
			  cplex.end( );
		}
		catch( IloException e ){
			e.printStackTrace( );
		}
		
		
		
		
		
		
		
	}

	
	
	
	
	/******************************************************************
	 * reset values before solving the MDPC with different parameters.
	 *********************************/
	public void resetValues( Instance in ) {
		alphas = new int[in.Co][in.NP];
		betas = new int[in.Co][in.NP];
		capLevels_V = new int [in.ND][in.NL][in.NP];
		allocDem_q = new double [in.ND][in.NC][in.NP];
		costCarrier = new double [in.Co][in.NP];
		Z_UB = 0;
		Z_LB = 0;
		Z_gap = 0;
		sol_time = -1;
		setup_time = -1;
	}

	

	
	
	public void solveMDPC_testing( Instance in ){
		int[][] fixed_alphas = { 
				{1,0,1,0},
				{0,0,0,0},
				{1,0,1,0},
				{0,0,0,0}
				} ;
		
		int[][] fixed_betas = { 
				{1,1,1,1},
				{0,0,0,0},
				{1,1,1,1},
				{0,0,0,0}
				} ;
		
		solveMDPC_FixedAlphas( in, fixed_alphas, fixed_betas );
	}
	
	
	
	public void solveMDPC_FixedAlphas( Instance in , int[][] fixed_alphas, int[][] fixed_betas ){	
		long start1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		try {					
			IloCplex cplex = new IloCplex( );
			// IloCplex.LongAnnotation benders = cplex.newLongAnnotation("cpxBendersPartition");		
			
			/********************************** VARIABLES **************************************/
			/***********************************************************************************/												
			IloNumVar[][][] q = new IloNumVar[in.ND][in.NC][ ];		
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true)
						q[i][k] = cplex.numVarArray( in.NP, 0, Double.MAX_VALUE );
				}
			}
			IloNumVar[][][] V = new IloNumVar[in.ND][in.NL][];
			for(int i = 0; i < in.ND; i++){
				for(int a = 0; a < in.NL; a++){
					V[i][a] = cplex.boolVarArray( in.NP );				
				}
			}		
			IloNumVar[][] Cost = new IloNumVar[in.Co][ ];
			for(int e = 0; e < in.Co; e++){
				Cost[e] = cplex.numVarArray( in.NP, 0 , Double.MAX_VALUE );
			}
			
			/****************************** OBJECTIVE FUNCTION ******************************************/
			/********************************************************************************************/		
			IloLinearNumExpr objective = cplex.linearNumExpr( );
			for(int e = 0; e < in.Co; e++){
				for(int t = 0; t < in.NP; t++){
					objective.addTerm(1, Cost[e][t]);
				}
			}
			for(int i = 0; i < in.ND; i++){
				for(int k = 0; k < in.NC; k++){
					for(int t = 0; t < in.NP; t++){
						if(in.Slinks[i][k] == true)
							objective.addTerm(in.SCost[i][k], q[i][k][t]) ;
					}
				}
			}		
			cplex.addMinimize(objective);
			
			/********************************** CONSTRAINTS **************************************/
			/*************************************************************************************/
			// C1: Customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint. constraints = in.NC*in.NP
			for(int k = 0; k < in.NC; k++){
				for(int t = 0; t < in.NP; t++){
					IloLinearNumExpr c1 = cplex.linearNumExpr( );
					for(int i = 0; i < in.ND; i++){
						if(in.Slinks[i][k] == true)
							c1.addTerm( 1, q[i][k][t] );					
					}
					cplex.addGe(c1, in.Dem[k][t]);
				}
			}
			// C2: Arc capacity. Only open facilities can deliver goods to customers. constraints = in.ND*in.NC*in.NP 		  
			for(int i = 0; i < in.ND; i++){
				  for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true){  
						for(int t = 0; t < in.NP; t++){
							IloLinearNumExpr c2 = cplex.linearNumExpr( );
							for(int a = 0; a < in.NL; a++){
								c2.addTerm( in.Dem[k][t], V[i][a][t] );
							}
							cplex.addLe(q[i][k][t], c2);
						}
					}
				  }
			}  	  
			// C3: Node Capacity. Enough Large Transportation capacity to carry depot's allocated demand. 		  
			for(int i = 0; i < in.ND; i++){
					for(int t = 0; t < in.NP; t++){
						IloLinearNumExpr c3 = cplex.linearNumExpr( );
						IloLinearNumExpr c3b = cplex.linearNumExpr( );
						for(int k = 0; k < in.NC; k++){
							if(in.Slinks[i][k] == true)																		
								c3.addTerm(1, q[i][k][t]);						
						}
						for(int a = 0; a < in.NL; a++){
							c3b.addTerm(in.lvQ[a], V[i][a][t]);						
						}					
						cplex.addLe(c3, c3b);
					}
			}
			// C4: Companies costs per period
			for(int e = 0; e < in.Co; e++){
					for(int t = 0; t < in.NP; t++){								
						IloLinearNumExpr TCosts = cplex.linearNumExpr( );					
						for(int i = 0; i < in.ND; i++){
							if(in.owners[i] == e+1){								
								for( int a = 0; a < in.NL; a++ ){
									TCosts.addTerm( in.LCost[i][a] + in.FCost[i], V[i][a][t] );
								}
							}
						}
						cplex.addGe(Cost[e][t], TCosts);
					}
			}
			// Relation between contracts and network constraints******************/
			// C5: mpc: If the contract is active it should be charged a lump sum. 		  
			for(int e = 0; e < in.Co; e++){		  
				for(int t = 0; t < in.NP; t++){
					  double c7 = in.mpc[e][t] * fixed_betas[e][t];
					  cplex.addGe( Cost[e][t], c7 );
				 }
			 }
			 // C6: Consistency of depots. If there is one depot open the contract is Active.
			 for(int e = 0; e < in.Co; e++){
				  for(int i = 0; i < in.ND; i++){
					  if(in.owners[i] == e+1){
							for(int t = 0; t < in.NP; t++){	
								IloLinearNumExpr Ve = cplex.linearNumExpr( );
								for(int a = 0; a < in.NL; a++){
									Ve.addTerm(1, V[i][a][t]);
								}
								cplex.addLe(Ve, fixed_betas[e][t]);
							}
					  }
				  }
			 }
			  /************************************ CPLEX PARAMETERS*******************************/
			  /************************************************************************************/
			  cplex.setOut(null);
			  //cplex.setParam( IloCplex.Param.Benders.Strategy, st );
			  cplex.setParam( IloCplex.DoubleParam.TiLim, 3600 );
			  //cplex.setParam( IloCplex.IntParam.Threads, 4 );
			  cplex.setParam( IloCplex.IntParam.MIPDisplay, 3 );
			  //cplex.setParam( IloCplex.IntParam.ParallelMode, 0 );
			  //cplex.setParam( IloCplex.IntParam.Threads, 1 );
			  long finish1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
			  long start2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
			  
			  if( cplex.solve( ) ) { 
			  long finish2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
			  //--------------- Copy decision variables values ------>>>>>>
			  for(int e = 0; e < in.Co; e++){ 
			  for(int t = 0; t < in.NP; t++){	
				  alphas[e][t] = fixed_alphas[e][t]; 
				  betas[e][t] = fixed_betas[e][t]; ;
				  costCarrier[e][t] = in.round( cplex.getValue( Cost[e][t] ), 2 );
				  for(int i = 0; i < in.ND; i++){	 
					  for( int a = 0; a < in.maxNL[i][t]; a++ ){
						  capLevels_V[i][a][t] = (int) in.round( cplex.getValue( V[i][a][t] ), 2 );	
					  }
					  for(int k = 0; k < in.NC; k++) {
						  if( in.Slinks[i][k] )
						  allocDem_q[i][k][t] = cplex.getValue( q[i][k][t] );
					  }
				  }
			  	}}
			  	//--------------- Copy (best found) solution values ------>>>>>>
				Z_UB = cplex.getObjValue( );
				Z_LB = cplex.getBestObjValue( );
				Z_gap = cplex.getMIPRelativeGap( );
				sol_time = finish2 - start2;
				setup_time = finish1 - start1;
			  }
			  cplex.end( );
		}
		catch( IloException e ){
			e.printStackTrace( );
		}
		/***************************************** CPLEX OUTPUT *****************************************/
		//printBasicSolutionValues ( in , "B&C" );
		printCompleteSolution( in, "B&C" );	
		printCarriersData( in );
		System.out.println( );
		printMPC_UtilizationData( in );
		System.out.println( );
		printCostsData( in );
		
	}
	
	
	
	
	
	
	
	


	

	
}
