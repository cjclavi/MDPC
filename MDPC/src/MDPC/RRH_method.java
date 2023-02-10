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
import ilog.cplex.IloCplex.Status;


/**
 * Clase con metodos para resolver una instancia del problema MDPC
 * RRH: Resuelve el problema MDPC por medio de Heuristica: Relax & Repair
 */
public class RRH_method extends Solution_method{
	/***********************************************************************************************
	 * ATTRIBUTES
	 ************************************************************************************************/
		
		//------- RRH solution ---------
		private double[] costs_heuristic;
		private double lb_time;
		private double heur_time;
		
		
	/***********************************************************************************************
	 * CONSTRUCTOR
	 * Crea una instancia de la clase RRH_method que puede llamar o correr los metodos de solucion
	 **************************************************/
		public RRH_method( Instance in ){
			super (in) ;
			lb_time = -1;
			heur_time = -1;
			costs_heuristic = new double[in.NP];																																																																																																																																																																																																																																																																																																																																																																																																																																																																				
		}
		
		
	/*******************************************************************************************************************************
	 * METHODS
	 ******************************************************************************************************************/
	/******************************************
	 * Main Problem (Integer V_{i,a})
	 *******************/
	public void solveMDPC( Instance in , int st ){	
		long start = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		//solve the MDPC for 1 period contracts and retrieve alpha variables.
		solveMDPC_1CT( in );
		System.out.println( );
		//printBetas( in );
		long finish1 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );
		repairContractsHeuristic( in );
		long finish2 = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) );	
		
		// ----------- Copy (best found) solution values ----------- // >>>>>>
		Z_gap = (Z_UB - Z_LB)/ Z_UB;
		lb_time = finish1 - start;
		heur_time = finish2 - finish1;
		sol_time = finish2 - start;
		//setup_time = finish1 - start;
		
		/***************************************** RRH OUTPUT *****************************************/
		printOutput_1( in, "RRH" );
	}
	
	
	
	public void solveMDPC_1CT( Instance in ) {
		// TODO Auto-generated method stub
		for(int t = 0; t < in.NP; t++) {
			solveMDPC_1P( in, t ); 
			Z_LB += costs_heuristic[t];
		}
	}

	
	/********************************
	 * Solve the MDPC problem period per period for mct = 1  (SDPC)
	 * @param in instance
	 */
	public void solveMDPC_1P( Instance in, int time ){
		
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
				  for(int e = 0; e < in.Co; e++)		   
				  betas[e][time] = (int) in.round( cplex.getValue( Alpha[e] ), 2  );
				  costs_heuristic[time] = cplex.getObjValue( ); 
			  }
			} catch (IloException e) {
				// TODO: handle exception
				// return null; 
			}
	
		
	}	
	
	
	
	
	
	public void repairContractsHeuristic( Instance in ) {

		int[][] repHeurSolution = new int[in.Co][in.NP];
		// copy solution from phase 1, in repHeursolution matrix. 
		for(int e = 0; e < in.Co; e++){
			for(int t = 0; t < in.NP; t++)
				repHeurSolution[e][t] = betas[e][t]; 	
		}		
		int infeasibilities = 0; // number of unfinished contracts in the solution.
		double final_Cost_t[] = new double[in.NP]; // cost of 't' after repairing.
		
		// ---- Feasibility checking and repairing procedure ---- //
		boolean infeasible;  
		boolean feas = true; // the contract length relaxation is/not fesible
		
		for(int t = 1; t < repHeurSolution[0].length; t++){		
			for(int e = 0; e < repHeurSolution.length; e++){
				// -------> FEASIBILITY CHECKING <---------- //
				// nalp -> number of continuous alphas = 1. tracing back 
				int nalp = 0; int ralp = 0;	infeasible = false;			
				if( repHeurSolution[e][t] == 0 ){		 
					int n = 1; 
					while( t-n > -1 && repHeurSolution[e][t-n] > 0 ){ // count only when there is a 1 before. 
						nalp++; n++;
					}					
					if( nalp % in.mct > 0 ){ 
						infeasible = true;
						feas = false;
						infeasibilities++;
					}
					// ralp -> residual alphas : number of periods to REMOVE contract (r)
					ralp = nalp % in.mct; 
				}
				// -------> REPAIRING PROCEDURE <-------- //
				if( infeasible ){						
					int[ ] co = new int[in.Co];	// new values of alphas, after modifiyng.	
					double costRemove = 0; double costComplete = 0; 
				
					//Calculate added value of removing (infeasible) contract. 	
					boolean remove_inf = false;
					for(int k = 1; k <= ralp && remove_inf == false; k++){						
						//copy alphas for period [t-k]
						for(int en = 0; en < co.length; en++)
							co[en] = repHeurSolution[en][t-k] ; 
						// remove contract of company (e) at period t-k.
						co[e] = 0;
						//Solve the SP(alphas) model.
						double newCost = solve_SPmodel( in, t-k, co, 0 );
						// compute cost of removing.  totalCostt[t] --> cost of period t after phase 1. 
						if(newCost > 0) {
							costRemove += ( newCost - costs_heuristic[t-k] );
						}else {
							costRemove = Double.MAX_VALUE; remove_inf = true;
						}
					}			
					
					//Calculate added value of completing (infeasible) contracts.
					//in.MCT -ralp: number of periods to COMPLETE the contract
					for(int k = 0; k < in.mct-ralp; k++){ 
						if(t+k < in.NP){
						//copy alphas for periods from t. [t+k]
						for(int en = 0; en < co.length; en++)
							co[en] = repHeurSolution[en][t+k]; 
									
						// modify alpha to remove contract, only if alpha_e^{t+k} =  0.
						if( repHeurSolution[e][t+k] < 1){ 
							co[e] = 1; 
							//Solve the SP(alphas) model.
							double newCost = solve_SPmodel( in, t+k, co, 0 );
							// compute cost of completing.  totalCostt[t] --> cost of period t after phase 1. 
							costComplete +=  newCost - costs_heuristic[t+k];;
						}}
					}
					// -------- Compare costs and modify solution ----------			
					if( costRemove < costComplete ){											
						for(int k = 1; k <= ralp; k++)
							repHeurSolution[e][t-k] = 0; 									
					}else{
						for(int k = 0; k < in.mct-ralp; k++)
							if(t+k < in.NP)	repHeurSolution[e][t+k] = 1;							
					}
				}
			}
		}
		// -------- COMPUTE THE FINAL COSTS AFTER REPAIRING ------ //
		
		// tCost -> final cost after repairing the alphas.
		double tCost = 0;
		if( !feas ){	
			// Solve the SP with final FEASIBLE solution		
			for(int t = 0; t < in.NP; t++){
				int co[] = new int[in.Co];
				for(int e = 0; e < in.Co; e++) {
					co[e] = repHeurSolution[e][t];
					betas[e][t] = repHeurSolution[e][t];  // final solution
				}
				final_Cost_t[t] = solve_SPmodel( in, t, co, 1 );
				tCost += final_Cost_t[t];
			}
			
		} else {
			for(int t = 0; t < in.NP; t++){
				int co[] = new int[in.Co];
				for(int e = 0; e < in.Co; e++) 
					co[e] = betas[e][t];	
				final_Cost_t[t] = solve_SPmodel( in, t, co, 1 );
				tCost += final_Cost_t[t];
			}
		}
		Z_UB = tCost;
	}
	
	

	/*****
	 * solve the SP model with fixed betas.
	 * @param output = 1 copy final solution values to attributes.
	 * @return the objective value
	 */
	public double solve_SPmodel( Instance in, int time, int[] co, int output ) {
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
		  // C5: Consistency of depots. If there is one depot open the contract is Active.		  
		  for(int e = 0; e < in.Co; e++){
			  for(int i = 0; i < in.ND; i++){
				  if(in.owners[i] == e+1){
						IloLinearNumExpr Ve = cplex.linearNumExpr( );
						for(int a = 0; a < in.NL; a++){
							Ve.addTerm( 1, V[i][a] );
						}						
						cplex.addLe( Ve, co[e] );
				  }
			  }			  
		  }
		  // C6: in.mpc: If the contract is active it should be charged an denominated amount. 		  
		  for(int e = 0; e < in.Co; e++){					 
			  cplex.addGe( Cost[e], co[e] * in.mpc[e][time] );
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
						objective.addTerm(in.SCost[i][k], q[i][k]);					
				}
		  }		
		  cplex.setOut( null );
		  cplex.setParam( IloCplex.IntParam.MIPDisplay, 0 );				
		  //sub.setParam( IloCplex.IntParam.Threads, 4 );
	      //sub.setParam( IloCplex.DoubleParam.TiLim, 200 );
		  cplex.addMinimize(objective);		
		  double obj = 0;
		  if( cplex.solve( ) ) {
			  obj = cplex.getObjValue( );
			  if(output == 1) {
				  for(int e = 0; e < in.Co; e++){ 
				  costCarrier[e][time] = in.round( cplex.getValue( Cost[e] ), 2 );
					  for(int i = 0; i < in.ND; i++){	 
						  for( int a = 0; a < in.maxNL[i][time]; a++ )
							  capLevels_V[i][a][time] = (int) in.round( cplex.getValue( V[i][a] ), 2 );	
						  for(int k = 0; k < in.NC; k++) {
							  if( in.Slinks[i][k] )
							  allocDem_q[i][k][time] = cplex.getValue( q[i][k] );
						  }
					  }
				  }
			  }
			  return obj;
		  }
		/************************************ CPLEX PARAMETERS*******************************/
	    /************************************************************************************/

		} catch (IloException e) {
			// TODO: handle exception
			// return null; 
		}
		
		return 0;
	}

	
}
