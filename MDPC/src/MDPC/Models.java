package MDPC;

import MDPC.Instance;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class Models {	

	/***************************************
	 * Solve the ISP for an specific company at a specific period.
	 * It determines the value of the MPC per period per company.
	 * @param in		 
	 * @param time
	 * @param co
	 * @return
	 */
	public static IloCplex createISP_EP( Instance in , int time, int co ){
		try{
		IloCplex cplex = new IloCplex( );						
		/********************************** VARIABLES ***************************************/
		/************************************************************************************/	
		IloNumVar[][] q = new IloNumVar[in.ND][in.NC];
		for(int i = 0; i < in.ND; i++){
			if(in.owners[i] == co){
				for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true) 
						q[i][k] = cplex.numVar( 0, Double.MAX_VALUE );				
				}
			}
		}		
		IloNumVar[][] V = new IloNumVar[in.ND][in.NL];
		for(int i = 0; i < in.ND; i++){
			if(in.owners[i]== co){
				for(int a = 0; a < in.NL; a++){
					V[i][a] = cplex.boolVar( );
				}
			}
		}
		IloNumVar Cost = cplex.numVar( 0 , Double.MAX_VALUE );
		
		/********************************** CONSTRAINTS **************************************/
		/*************************************************************************************/		
		// C1: customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint.
		for(int k = 0; k < in.NC; k++){					
			for( int i = 0; i < in.ND; i++ ){
				if(in.owners[i]== co && in.Slinks[i][k] == true){
					IloLinearNumExpr c1 = cplex.linearNumExpr( );
					for( int d = 0; d < in.ND; d++ ){
						if( in.owners[d]== co && in.Slinks[d][k] == true)
							c1.addTerm( 1, q[i][k] );
					}
					cplex.addGe( c1, in.Dem[k][time] );
				}									
			}			
		}		
		// C2: Only open facilities can deliver goods to customers.		
		for(int i = 0; i < in.ND; i++){
			if(in.owners[i]== co){
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
		}
		//	C3: Enough Large Transportation capacity to carry depot's allocated demand. 		  
		  for(int i = 0; i < in.ND; i++){
			  if(in.owners[i]== co){
					IloLinearNumExpr c3 = cplex.linearNumExpr( );
					IloLinearNumExpr c3b = cplex.linearNumExpr( );
					for( int k = 0; k < in.NC; k++ ){
						if( in.Slinks[i][k] == true )																		
							c3.addTerm(1, q[i][k]);						
					}
					for(int a = 0; a < in.NL; a++){
						c3b.addTerm(in.lvQ[a], V[i][a]);						
					}					
					cplex.addLe(c3, c3b);			
			  }
		  }		  
		  // C4: Companies costs per period															
		  IloLinearNumExpr TCosts = cplex.linearNumExpr( );					
		  for(int i = 0; i < in.ND; i++){
			  if(in.owners[i] == co){										
				  for(int a = 0; a < in.NL; a++){
					  TCosts.addTerm(in.FCost[i] + in.LCost[i][a], V[i][a]);
				  }
	  		  }
		  }							
		  cplex.addGe(Cost, TCosts);	
		  
		  // C5: Consistency of depots. If there is one depot open the contract is Active.		  	
		  for(int i = 0; i < in.ND; i++){
			  if(in.owners[i] == co){
					IloLinearNumExpr Ve = cplex.linearNumExpr( );
					for(int a = 0; a < in.NL; a++){
						Ve.addTerm(1, V[i][a]);
					}											
					cplex.addLe(Ve, 1);
			  }
		  }			  		  		  		  		  
		  /************************************ OBJECTIVE FUNCTION ****************************************/
		  /************************************************************************************************/		  				
			IloLinearNumExpr objective = cplex.linearNumExpr( );
			objective.addTerm( 1, Cost );
			/**for(int i = 0; i < in.ND; i++){
				if( in.owners[i] == co ){
					for(int k = 0; k < in.NC; k++){
						if( in.Slinks[i][k] == true )
							objective.addTerm(in.SCost[i][k], q[i][k]);					
					}
				}
			}*/
			cplex.addMinimize( objective );
			cplex.setParam( IloCplex.IntParam.MIPDisplay, 0 );
			return cplex;
		}catch(IloException e){
			return null;
		}
	}
	
	

	
	
	/*******************************************************************************************************
	 * Minimum MPC covering problem
	 ***********************************************************/
	public double minMPCCost( Instance in ){
		
		try{
		IloCplex cplex = new IloCplex( );	
		/********************************** VARIABLES ***************************************/
		IloNumVar[][] Cost = new IloNumVar[in.Co][ ];
		for(int e = 0; e < in.Co; e++){
				Cost[e] = cplex.numVarArray( in.NP, 0 , Double.MAX_VALUE );
		}
		IloNumVar[][] alpha = new IloNumVar[in.Co][];
		for(int e = 0; e < in.Co; e++){
			alpha[e] = cplex.boolVarArray( in.NP );
		}
		
		/***************************** OBJECTIVE FUNCTION ************************************/
		IloLinearNumExpr objective = cplex.linearNumExpr( );
		for(int e = 0; e < in.Co; e++){
				for(int t = 0; t < in.NP; t++){			
					objective.addTerm( 1, Cost[e][t] );
				}
		}
		cplex.addMinimize(objective);
		/********************************** CONSTRAINTS **************************************/
		for(int e = 0; e < in.Co; e++){		  
			  for(int t = 0; t < in.NP; t++){
				  IloLinearNumExpr c7 = cplex.linearNumExpr( );
				  c7.addTerm(in.mpc[e][t], alpha[e][t] );
				  cplex.addGe(Cost[e][t], c7);
			 }
		}
		// CC1: There should be least one open company linked with each customer.
		// Thus, and mpc is charged.
		for(int k = 0; k < in.NC; k++){
			  for(int t = 0; t < in.NP; t++){
				  IloLinearNumExpr cc1 = cplex.linearNumExpr( );
				  for(int e = 0; e < in.Co; e++){
					  if ( in.companyCanDeliver(e,k) )
						  cc1.addTerm( 1, alpha[e][t] );
				  }
				  cplex.addGe( cc1 , 1 ); 
			  }
		}
		cplex.setOut( null );
		//System.out.println( cplex.getStatus().toString());
		cplex.solve( );
		//System.out.println( cplex.getStatus().toString());
		if( cplex.solve( ) ){  
			  return cplex.getObjValue( );
			  
		}} catch(IloException e){
			return (Double) null;
		}
		return 1;
		
	}
	
	
	/**************************************************************************************************
	 *  Minimum carrier's operational costs covering problem
	 **************************************/
	public double minOpCost( Instance in ){
		double minOpCarCost = 0;
		try{
		IloCplex cplex = new IloCplex( );	
		/********************************** VARIABLES ***************************************/
		IloNumVar[][][] q = new IloNumVar[in.ND][in.NC][ ];
		for(int i = 0; i < in.ND; i++){
			for(int k = 0; k < in.NC; k++){
					if(in.Slinks[i][k] == true)
						q[i][k] = cplex.numVarArray( in.NP, 0, Double.MAX_VALUE );
			}
		}
		IloNumVar[][][] V = new IloNumVar[in.ND][in.NL][ ];
		for(int i = 0; i < in.ND; i++){
			for(int a = 0; a < in.NL; a++){
				V[i][a] = cplex.boolVarArray( in.NP );
			}
		}
		IloNumVar[][] Cost = new IloNumVar[in.Co][ ];
		for(int e = 0; e < in.Co; e++){
				Cost[e] = cplex.numVarArray( in.NP, 0 , Double.MAX_VALUE );
		}
		/****************************** OBJECTIVE FUNCTION ************************************/
		
		IloLinearNumExpr objective = cplex.linearNumExpr( );
		for(int e = 0; e < in.Co; e++){
				for(int t = 0; t < in.NP; t++){			
					objective.addTerm(1, Cost[e][t]);
				}
		}
		cplex.addMinimize(objective);
		
		/********************************** CONSTRAINTS ****************************************/
		
		// C1: Customer satisfaction. Each customer(route) is satisfied. Single-sourced constraint. constraints = in.NC*RHL
		for(int k = 0; k < in.NC; k++){
			for(int t = 0; t < in.NP; t++){
					IloLinearNumExpr c1 = cplex.linearNumExpr( );
					for(int i = 0; i < in.ND; i++){
						if(in.Slinks[i][k] == true)
							c1.addTerm(1, q[i][k][t]);					
					}
					cplex.addGe(c1, in.Dem[k][t]);
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
						c3b.addTerm( in.lvQ[a], V[i][a][t] );						
					}					
					cplex.addLe(c3, c3b);
				}
		  }
		
		// C4 (a): Companies costs: Cost of discounted plus spot price ressources
		  for(int e = 0; e < in.Co; e++){
				for(int t = 0; t < in.NP; t++){								
					IloLinearNumExpr TCosts = cplex.linearNumExpr( );
					for(int i = 0; i < in.ND; i++){
						if(in.owners[i] == e+1){								
							for(int a = 0; a < in.maxNL[i][t]; a++){
								TCosts.addTerm( in.LCost[i][a], V[i][a][t] );
							}
						}
					}
					cplex.addGe( Cost[e][t], TCosts );
				}
		  }
		
		// C5: Only one capacity level could be selected per depot.
		for(int i = 0; i < in.ND; i++){
				for(int t = 0; t < in.NP; t++){	
					IloLinearNumExpr Ve = cplex.linearNumExpr( );
					for(int a = 0; a < in.maxNL[i][t]; a++){
						Ve.addTerm( 1, V[i][a][t] );
					}
					cplex.addLe(Ve, 1);
				}
		}
		cplex.setOut( null );
		cplex.setParam( IloCplex.IntParam.MIPDisplay, 0 );
		cplex.setParam( IloCplex.DoubleParam.TiLim, 600 );
		
		if( cplex.solve( ) ) {
			minOpCarCost = cplex.getObjValue( );
		}} catch( IloException e )	{
				return (Double) null;
		}
		return minOpCarCost;
	}
	
	
	

}
