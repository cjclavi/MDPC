package MDPC;

import ilog.concert.IloException;

public abstract class Solution_method {
	
	
	//------ decision variables --------
	protected int[][] alphas; //DV
	protected int[][] betas; //DV
	protected int[][][] capLevels_V; //DV 
	protected double [][][] allocDem_q; 
	protected double [][] costCarrier; // DV
	
	
	//------ solution values ------
	protected double Z_UB;
	protected double Z_LB;
	protected double Z_gap;
	protected double sol_time;
	protected double setup_time;
	
	
	
	
	
	public Solution_method( Instance in ){
		alphas = new int[in.Co][in.NP];
		betas = new int[in.Co][in.NP];
		capLevels_V = new int [in.ND][in.NL][in.NP];
		allocDem_q = new double [in.ND][in.NC][in.NP];
		costCarrier = new double [in.Co][in.NP];
		Z_UB = 0;
		Z_LB = 0;
		Z_gap = -1;
		sol_time = -1;
		setup_time = -1;
	}
	
	

	/********************************************************************
	 *  Methods that solve an instance of the MDPC problem according to the CT
	 ***********************************/
	
	public void solveMDPC( Instance in ) {/*...*/}
	
	
	public abstract void solveMDPC( Instance in , int arg ) throws IloException;
	

	public void solveMDPC_1CT( Instance in ){/*...*/}
	
	
	public void solveMDPC_FCT( Instance in , int arg ){/*...*/} 
	
	
	
	
	
	
	/********************************************************************
	 * Methods that print the output of a solution of an MDPC instance.
	 ***********************************/
	
	//Method that prints the contract solution (best found, optimal). Opening contracts periods. 
	public void printAlphas( Instance in ) {
		for(int e = 0; e < in.Co; e++){	 
				System.out.print( " Car " + e + " : " ); 
			 	for(int t = 0; t < in.NP; t++) {
			 		System.out.print(  alphas[e][t]    + " | ");
			 	}	
			 	System.out.println( );
		}
	}
	
	
	
	//Method that prints the contract solution (best found, optimal). Active contracts periods 
	public void printBetas( Instance in ) {
			for(int e = 0; e < in.Co; e++){	 
				System.out.print( " Car " + e + " : " ); 
			 	for(int t = 0; t < in.NP; t++) {
			 		System.out.print(  betas[e][t]    + " | ");
			 	}	
			 	System.out.println( );
			}
	}
	
	
	

	public void printBasicSolutionValues( Instance in , String st ){
		  System.out.println( ) ; 
		  System.out.println( ">>>>>>>>>>>>>>>>>>>>>>>>>>> Solution method: " +st+ " <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<" ) ;
		  System.out.println( ) ; 
		  String format = "%-15s %12s";
		  System.out.format(format, "Comp. Time : ", sol_time +"s ("+ setup_time + "s)" ) ; System.out.println( ); 
		  System.out.format(format, "Best Value : ", in.round( Z_UB ,2 ) ) ;
		  System.out.println( ); System.out.format(format, "LB : ", in.round( Z_LB ,2 ) ) ; System.out.println( );
		  System.out.format(format, "GAP : ", in.round( Z_gap ,5 ) ) ;
		  System.out.println( ); System.out.println( ); 
		  printBetas( in );
		  System.out.println( ); 
	}
		 
		
		
	
	/*************************
	 * Print  MPC utilization data (%) + resource utilization per carrier and period
	 */
	public void printCompleteSolution( Instance in, String st ) {
		  printBasicSolutionValues(in, st);	
		  double[] mpc_ut_avg = new double[in.NP];
		  int[] total_carriers = new int[in.NP];
		  int[] total_depots = new int[in.NP];
		  int[] total_vehicles = new int[in.NP];
		  
		  for(int e = 0; e < in.Co; e++){	System.out.print(  "Car: "+e + "  | " ); 
		  for(int t = 0; t < in.NP; t++){	
			  // contract + mpc_utilization + ratio [D/|I_e|]  + Sum_{V_i,a}
			  String format = "%3s %4s [%-3s, %2s] |"; String format2 = "%20s";
			  String contract = "";
			  String mpc_utilization = "";
			  String ratioDepots = "";
			  String vehicle_utilization = "";
			  if( betas[e][t] == 1 )  {
				  if( alphas[e][t] == 1 ) contract = "(1)"; else contract = "   ";; 
				  total_carriers[t]++; 
				  int trucksUsed = 0; // trucks used under contracts
				  int depotsUsed = 0;
				  double payment = 0;
				  for(int i = 0; i < in.ND; i++){	
					  if( in.owners[i] == e+1 ) {
						  for( int a = 0; a < in.maxNL[i][t]; a++ ){
							  if( capLevels_V[i][a][t] == 1 ) { 
							  trucksUsed += a+1 ; // total vehicles used assuming 1 level = 1 vehicle;
							  payment +=  ( in.FCost[i] + ( in.LCost[i][a] ));  //* (a+1) )  );
							  depotsUsed++;
							  }
						  }
					  }
				  }
				  int mpc_ut = (int)( in.round( payment / in.mpc[e][t], 2 ) * 100 );
				  mpc_utilization = Integer.toString( mpc_ut  ) + "%";
				  ratioDepots = Integer.toString(depotsUsed) + "/" + in.getDepots(e); 
				  vehicle_utilization = Integer.toString(trucksUsed);
				  //----------------------
				  mpc_ut_avg[t] +=  payment/in.mpc[e][t];
				  total_depots[t] += depotsUsed;
				  total_vehicles[t] += trucksUsed;
				  System.out.format( format, contract, mpc_utilization,  ratioDepots,  vehicle_utilization );
			  } else System.out.format( format2, " |" );
			  
		  } System.out.println( );
		  }
		  String line = "";
		  int wide = 10 +(20 * in.NP);
		  for(int n=0;  n<wide; n++) 
			  line += "-";		  
		  System.out.println( line );
		  String format2 = "%8s [%-3s, %2s] |";
		  System.out.print(  "Total   | " ); 
		  for(int t = 0; t < in.NP; t++) {	
			  	int mpc_avg = (int) ( in.round( mpc_ut_avg[t]/total_carriers[t] , 2 )*100 );
		  		System.out.format( format2, mpc_avg+"%" , total_depots[t],  total_vehicles[t] );
		  }System.out.println( );
		  System.out.println( );
	}
	
	
	
	
	public void printCarriersData( Instance in ){
		int perm_carriers = 0;
		int ocas_carriers = 0;
		double perc_carriers_ls = 0;
		double perc_carriers_hs = 0;
		double perc_carriers_ms = 0;
		double tot_carriers_ls = 0;
		double tot_carriers_hs = 0;
		double tot_carriers_ms = 0;
		int periods_ls = 0;
		int periods_hs = 0;
		int periods_ms = 0;
		// -----------
		for(int e = 0; e < in.Co; e++) {	
			int periodsActive = 0;
			for(int t = 0; t < in.NP; t++){
				if( betas[e][t] == 1 ) {
					periodsActive ++;
					if ( in.getSeason(t) == "LOW" ) tot_carriers_ls ++; 
					if ( in.getSeason(t) == "HIGH" )  tot_carriers_hs ++;
					if ( in.getSeason(t) == "MEDIUM" )  tot_carriers_ms ++;
				}
			}
			if(periodsActive == in.NP) perm_carriers++;
			if(periodsActive > 0 && periodsActive < in.NP)  ocas_carriers++;
		}
		for(int t = 0; t < in.NP; t++){
			if ( in.getSeason (t) == "LOW" ) periods_ls++; 
			if ( in.getSeason (t) == "HIGH" )  periods_hs++;
			if ( in.getSeason (t) == "MEDIUM" )  periods_ms++;
		}
		int total_carriers = perm_carriers + ocas_carriers;
		perc_carriers_ls = tot_carriers_ls*100 / (periods_ls * in.Co * 1.0);
		perc_carriers_hs = tot_carriers_hs*100 / (periods_hs * in.Co * 1.0);
		perc_carriers_ms = tot_carriers_ms*100 / (periods_ms * in.Co * 1.0);
		
		String format = "%-15s %6s";
		System.out.println( "CARRIERS DATA" );
		System.out.format( format, "Total Carriers : ", total_carriers + "/" + in.Co );
		System.out.println( ); 
		System.out.format( format, "Permanent:", perm_carriers );
		System.out.println( ); 
		System.out.format( format, "Some Periods:", ocas_carriers );
		System.out.println( ); 
		System.out.format( format, "% Carriers LS Periods (Avg):", in.round(perc_carriers_ls, 3)+" %" );
		System.out.println( ); 
		System.out.format( format, "% Carriers HS Periods (Avg):", in.round(perc_carriers_hs, 3)+" %" );
		System.out.println( ); 
		System.out.format( format, "% Carriers MS Periods (Avg):", in.round(perc_carriers_ms, 3)+" %" );
		System.out.println( ); 
	}
	
	
	
	
	public void printMPC_UtilizationData( Instance in ) {
		System.out.println( "MPC DATA PER PERIOD");
		double[] tot_payment = new double[in.NP];
		double[] tot_mpc = new double[in.NP];
		int times_ab_mpc = 0; int tot_times = 0;
		int times_ab_mpc_hs = 0;    int tot_times_hs = 0;
		int times_ab_mpc_ms = 0;	int tot_times_ms = 0;
		int times_ab_mpc_ls = 0;	int tot_times_ls = 0;
	
		int times_beleq_mpc = 0;
		int times_beleq_mpc_hs = 0;    
		int times_beleq_mpc_ms = 0;	
		int times_beleq_mpc_ls = 0;	
		
		double MPCpayments = 0;
		double MPCpayments_hs = 0;
		double MPCpayments_ms = 0;
		double MPCpayments_ls = 0;
		
		double extraPayments = 0;
		double extraPayments_hs = 0;
		double extraPayments_ms = 0;
		double extraPayments_ls = 0;
		
		for(int e = 0; e < in.Co; e++){	System.out.print(  "Car: "+e + " |" ); 
		for(int t = 0; t < in.NP; t++){	
			// contract + ratioPayment + mpc_utilization
			String format = "%1s (%-12s=%5s) |";  String format2 = "%24s";
			String contract = " ";
			String ratioPayment = "";
			String mpc_utilization = "";
			if( betas[e][t] == 1 )  {
				  if( alphas[e][t] == 1 ) contract = "1"; 
				  tot_times++;  
				  //System.out.print ( in.getSeason (t) );
				  if ( in.getSeason (t) == "HIGH" ) 	tot_times_hs++;
				  if ( in.getSeason (t) == "MEDIUM" )   tot_times_ms++;
				  if ( in.getSeason (t) == "LOW" )   	tot_times_ls++;
				  double payment = 0;
				  for(int i = 0; i < in.ND; i++){	
					  if( in.owners[i] == e+1 ) {
						  for( int a = 0; a < in.maxNL[i][t]; a++ ){
							  if( capLevels_V[i][a][t] == 1 ) { 
								  payment += ( in.FCost[i] + ( in.LCost[i][a] ) );	//* (a+1) );
							  }
						  }
					  }
				  }
				  int ratio_Payment = (int) in.round( payment, 0 );
				  ratioPayment = Integer.toString( ratio_Payment ) + "/" + in.round( in.mpc[e][t], 1);
				  mpc_utilization = Integer.toString( (int) ( in.round( payment/in.mpc[e][t], 2 ) * 100 ) ) + "%";
				  tot_payment[t] += payment;
				  tot_mpc[t] += in.mpc[e][t];
				  MPCpayments += in.mpc[e][t];
				  if ( in.getSeason (t) == "HIGH" )  MPCpayments_hs += in.mpc[e][t];
				  if ( in.getSeason (t) == "MEDIUM" )  MPCpayments_ms += in.mpc[e][t];
				  if ( in.getSeason (t) == "LOW" )  MPCpayments_ls += in.mpc[e][t];
				  if( payment > in.mpc[e][t] ) { 
					    times_ab_mpc++;
					    if ( in.getSeason (t) == "HIGH" ) times_ab_mpc_hs++;
					    if ( in.getSeason (t) == "MEDIUM" )   times_ab_mpc_ms++;
					    if ( in.getSeason (t) == "LOW" )   times_ab_mpc_ls++;
				  		extraPayments += payment - in.mpc[e][t] ;
				  		if ( in.getSeason (t) == "HIGH" )  extraPayments_hs += payment - in.mpc[e][t] ;
					    if ( in.getSeason (t) == "MEDIUM" )  extraPayments_ms += payment - in.mpc[e][t] ;
			        	if ( in.getSeason (t) == "LOW" )  extraPayments_ls += payment - in.mpc[e][t];
				  }
				  else {
					  times_beleq_mpc++;
					  if ( in.getSeason (t) == "HIGH" )   times_beleq_mpc_hs++;
					  if ( in.getSeason (t) == "MEDIUM" )   times_beleq_mpc_ms++;
				      if ( in.getSeason (t) == "LOW" )   times_beleq_mpc_ls++;
				  }
				  System.out.format( format, contract, ratioPayment, mpc_utilization );
			} else System.out.format( format2, " |" );
			
		} System.out.println( );
		}
		String line = "";
		int wide = 8 +(24 * in.NP);
		for(int n=0;  n<wide; n++) 
			  line += "-";	
		System.out.println( line );
		System.out.print(  "Total  |" ); 
		String format2 = "   %-19s |";
		for(int t = 0; t < in.NP; t++) {	
				String ratio = Integer.toString( (int) in.round( tot_payment[t], 0 ) ) + "/" + 
							   Integer.toString(  (int) in.round( tot_mpc[t], 0 ) );
		  		System.out.format( format2, ratio );
		} System.out.println( );
		System.out.println( );
		
		
		
		// General information for the complete horizon
		System.out.println( "MPC DATA ");
		String format = "%-35s %10s";
		// Times above MPC (%)
		System.out.format( format, "Times above MPC : ", in.round(times_ab_mpc*100/tot_times, 2) +" % ("+ times_ab_mpc+"/"+tot_times  +") \n"   );	
		System.out.format( format, "    (Low Season) : ", in.round(times_ab_mpc_ls*100/tot_times_ls, 2) +" % ("+ times_ab_mpc_ls+"/"+tot_times_ls  +") \n"   );
		System.out.format( format, "    (Medium Season) : ", in.round(times_ab_mpc_ms*100/tot_times_ms, 2) +" % ("+ times_ab_mpc_ms+"/"+tot_times_ms  +") \n"   );
		System.out.format( format, "    (High Season) : ", in.round(times_ab_mpc_hs*100/tot_times_hs, 2) +" % ("+ times_ab_mpc_hs+"/"+tot_times_hs  +") \n"   );
		System.out.println( );
		
		// Times below MPC (%)
		System.out.format( format, "Times below-equal MPC : " , in.round(times_beleq_mpc*100/tot_times, 2)+" % ("+times_beleq_mpc+"/"+tot_times+") \n" ); 
		System.out.format( format, "    (Low Season) : ", in.round(times_beleq_mpc_ls*100/tot_times_ls, 2) +" % ("+ times_beleq_mpc_ls+"/"+tot_times_ls  +") \n"   );
		System.out.format( format, "    (Medium Season) : ", in.round(times_beleq_mpc_ms*100/tot_times_ms, 2) +" % ("+ times_beleq_mpc_ms+"/"+tot_times_ms  +") \n"   );
		System.out.format( format, "    (High Season) : ", in.round(times_beleq_mpc_hs*100/tot_times_hs, 2) +" % ("+ times_beleq_mpc_hs+"/"+tot_times_hs  +") \n"   );
		System.out.println( );
		
		
		// Percentage above MPC:
		System.out.format( format, "FTL costs (above MPC - MPC) :", in.round( extraPayments*100/ (extraPayments+MPCpayments), 2) +" % ("+
		in.round( extraPayments, 2 ) +") / " + in.round( MPCpayments*100/ (extraPayments+MPCpayments), 2) +" % ("+ in.round( MPCpayments, 2 ) +") \n" );
		System.out.format( format, "    (Low Season) : ",  in.round( extraPayments_ls*100/ (extraPayments_ls+MPCpayments_ls), 2) +" % ("+
				in.round( extraPayments_ls, 2 ) +") / " + in.round( MPCpayments_ls*100/ (extraPayments_ls+MPCpayments_ls), 2) +" % ("+ in.round( MPCpayments_ls, 2 ) +") \n" );
		System.out.format( format, "    (Medium Season) : " , in.round( extraPayments_ms*100/ (extraPayments_ms+MPCpayments_ms), 2) +" % ("+
				in.round( extraPayments_ms, 2 ) +") / " + in.round( MPCpayments_ms*100/ (extraPayments_ms+MPCpayments_ms), 2) +" % ("+ in.round( MPCpayments_ms, 2 ) +") \n"  );
		System.out.format( format, "    (High Season) : ",  in.round( extraPayments_hs*100/ (extraPayments_hs+MPCpayments_hs), 2) +" % ("+
				in.round( extraPayments_hs, 2 ) +") / " + in.round( MPCpayments_hs*100/ (extraPayments_hs+MPCpayments_hs), 2) +" % ("+ in.round( MPCpayments_hs, 2 ) +") \n" );
		System.out.println( );
		
	}	
	
	
	
	
	
	public void printMPC_UtilizationData2( Instance in ) {
		System.out.println( "MPC DATA PER PERIOD");
		double[] tot_payment = new double[in.NP];
		double[] tot_mpc = new double[in.NP];
		int times_ab_mpc = 0; int tot_times = 0;
		int times_ab_mpc_hs = 0;    int tot_times_hs = 0;
		int times_ab_mpc_ms = 0;	int tot_times_ms = 0;
		int times_ab_mpc_ls = 0;	int tot_times_ls = 0;
	
		int times_beleq_mpc = 0;
		int times_beleq_mpc_hs = 0;    
		int times_beleq_mpc_ms = 0;	
		int times_beleq_mpc_ls = 0;	
		
		double MPCpayments = 0;
		double MPCpayments_hs = 0;
		double MPCpayments_ms = 0;
		double MPCpayments_ls = 0;
		
		double extraPayments = 0;
		double extraPayments_hs = 0;
		double extraPayments_ms = 0;
		double extraPayments_ls = 0;
		
		for(int e = 0; e < in.Co; e++){	// System.out.print(  "Car: "+e + " |" ); 
		for(int t = 0; t < in.NP; t++){	
			// contract + ratioPayment + mpc_utilization
			String format = "%1s (%-12s=%5s) |";  String format2 = "%24s";
			String contract = " ";
			String ratioPayment = "";
			String mpc_utilization = "";
			if( betas[e][t] == 1 )  {
				  if( alphas[e][t] == 1 ) contract = "1"; 
				  tot_times++;  
				  //System.out.print ( in.getSeason (t) );
				  if ( in.getSeason (t) == "HIGH" ) 	tot_times_hs++;
				  if ( in.getSeason (t) == "MEDIUM" )   tot_times_ms++;
				  if ( in.getSeason (t) == "LOW" )   	tot_times_ls++;
				  double payment = 0;
				  for(int i = 0; i < in.ND; i++){	
					  if( in.owners[i] == e+1 ) {
						  for( int a = 0; a < in.maxNL[i][t]; a++ ){
							  if( capLevels_V[i][a][t] == 1 ) { 
								  payment += ( in.FCost[i] + ( in.LCost[i][a] ) );	//* (a+1) );
							  }
						  }
					  }
				  }
				  int ratio_Payment = (int) in.round( payment, 0 );
				  ratioPayment = Integer.toString( ratio_Payment ) + "/" + in.round( in.mpc[e][t], 1);
				  mpc_utilization = Integer.toString( (int) ( in.round( payment/in.mpc[e][t], 2 ) * 100 ) ) + "%";
				  tot_payment[t] += payment;
				  tot_mpc[t] += in.mpc[e][t];
				  MPCpayments += in.mpc[e][t];
				  if ( in.getSeason (t) == "HIGH" )  MPCpayments_hs += in.mpc[e][t];
				  if ( in.getSeason (t) == "MEDIUM" )  MPCpayments_ms += in.mpc[e][t];
				  if ( in.getSeason (t) == "LOW" )  MPCpayments_ls += in.mpc[e][t];
				  if( payment > in.mpc[e][t] ) { 
					    times_ab_mpc++;
					    if ( in.getSeason (t) == "HIGH" ) times_ab_mpc_hs++;
					    if ( in.getSeason (t) == "MEDIUM" )   times_ab_mpc_ms++;
					    if ( in.getSeason (t) == "LOW" )   times_ab_mpc_ls++;
				  		extraPayments += payment - in.mpc[e][t] ;
				  		if ( in.getSeason (t) == "HIGH" )  extraPayments_hs += payment - in.mpc[e][t] ;
					    if ( in.getSeason (t) == "MEDIUM" )  extraPayments_ms += payment - in.mpc[e][t] ;
			        	if ( in.getSeason (t) == "LOW" )  extraPayments_ls += payment - in.mpc[e][t];
				  }
				  else {
					  times_beleq_mpc++;
					  if ( in.getSeason (t) == "HIGH" )   times_beleq_mpc_hs++;
					  if ( in.getSeason (t) == "MEDIUM" )   times_beleq_mpc_ms++;
				      if ( in.getSeason (t) == "LOW" )   times_beleq_mpc_ls++;
				  }
				 // System.out.format( format, contract, ratioPayment, mpc_utilization );
			} //else // System.out.format( format2, " |" );
			
		} // System.out.println( );
		}
		String line = "";
		int wide = 8 +(24 * in.NP);
		for(int n=0;  n<wide; n++) 
			  line += "-";	
		//System.out.println( line );
		//System.out.print(  "Total  |" ); 
		String format2 = "   %-19s |";
		for(int t = 0; t < in.NP; t++) {	
				String ratio = Integer.toString( (int) in.round( tot_payment[t], 0 ) ) + "/" + 
							   Integer.toString(  (int) in.round( tot_mpc[t], 0 ) );
		  		//System.out.format( format2, ratio );
		}// System.out.println( );
		//System.out.println( );
		
		
		
		// General information for the complete horizon
		System.out.println( "MPC DATA ");
		String format = "%-35s %10s";
		// Times above MPC (%)
		System.out.format( format, "Times above MPC : ", in.round(times_ab_mpc*100/tot_times, 2) +" % ("+ times_ab_mpc+"/"+tot_times  +") \n"   );	
		System.out.format( format, "    (Low Season) : ", in.round(times_ab_mpc_ls*100/tot_times_ls, 2) +" % ("+ times_ab_mpc_ls+"/"+tot_times_ls  +") \n"   );
		System.out.format( format, "    (Medium Season) : ", in.round(times_ab_mpc_ms*100/tot_times_ms, 2) +" % ("+ times_ab_mpc_ms+"/"+tot_times_ms  +") \n"   );
		System.out.format( format, "    (High Season) : ", in.round(times_ab_mpc_hs*100/tot_times_hs, 2) +" % ("+ times_ab_mpc_hs+"/"+tot_times_hs  +") \n"   );
		System.out.println( );
		
		// Times below MPC (%)
		System.out.format( format, "Times below-equal MPC : " , in.round(times_beleq_mpc*100/tot_times, 2)+" % ("+times_beleq_mpc+"/"+tot_times+") \n" ); 
		System.out.format( format, "    (Low Season) : ", in.round(times_beleq_mpc_ls*100/tot_times_ls, 2) +" % ("+ times_beleq_mpc_ls+"/"+tot_times_ls  +") \n"   );
		System.out.format( format, "    (Medium Season) : ", in.round(times_beleq_mpc_ms*100/tot_times_ms, 2) +" % ("+ times_beleq_mpc_ms+"/"+tot_times_ms  +") \n"   );
		System.out.format( format, "    (High Season) : ", in.round(times_beleq_mpc_hs*100/tot_times_hs, 2) +" % ("+ times_beleq_mpc_hs+"/"+tot_times_hs  +") \n"   );
		System.out.println( );
		
		
		// Percentage above MPC:
		System.out.format( format, "FTL costs (above MPC - MPC) :", in.round( extraPayments*100/ (extraPayments+MPCpayments), 2) +" % ("+
		in.round( extraPayments, 2 ) +") / " + in.round( MPCpayments*100/ (extraPayments+MPCpayments), 2) +" % ("+ in.round( MPCpayments, 2 ) +") \n" );
		System.out.format( format, "    (Low Season) : ",  in.round( extraPayments_ls*100/ (extraPayments_ls+MPCpayments_ls), 2) +" % ("+
				in.round( extraPayments_ls, 2 ) +") / " + in.round( MPCpayments_ls*100/ (extraPayments_ls+MPCpayments_ls), 2) +" % ("+ in.round( MPCpayments_ls, 2 ) +") \n" );
		System.out.format( format, "    (Medium Season) : " , in.round( extraPayments_ms*100/ (extraPayments_ms+MPCpayments_ms), 2) +" % ("+
				in.round( extraPayments_ms, 2 ) +") / " + in.round( MPCpayments_ms*100/ (extraPayments_ms+MPCpayments_ms), 2) +" % ("+ in.round( MPCpayments_ms, 2 ) +") \n"  );
		System.out.format( format, "    (High Season) : ",  in.round( extraPayments_hs*100/ (extraPayments_hs+MPCpayments_hs), 2) +" % ("+
				in.round( extraPayments_hs, 2 ) +") / " + in.round( MPCpayments_hs*100/ (extraPayments_hs+MPCpayments_hs), 2) +" % ("+ in.round( MPCpayments_hs, 2 ) +") \n" );
		System.out.println( );
		
	}	
	
	
	
	public void printCostsData( Instance in ){
		double[] FTL_costs = new double[in.NP];
		double[] Dist_costs = new double[in.NP];
		double Tot_FTL_costs = 0;
		double Tot_Dist_costs = 0;
		double Tot_costs_ls = 0;
		double Tot_costs_ms = 0;
		double Tot_costs_hs = 0;
		
		for(int t = 0; t < in.NP; t++){	
			for(int e = 0; e < in.Co; e++){	
				if( betas[e][t] == 1 )  {
					  double ftl_costs = 0;	
					  for(int i = 0; i < in.ND; i++){	
						  if( in.owners[i] == e+1 ) {
							  for( int a = 0; a < in.maxNL[i][t]; a++ ){
								  if( capLevels_V[i][a][t] == 1 )  
								  ftl_costs += in.FCost[i] + ( in.LCost[i][a] );
							  }
							  for( int k = 0; k < in.NC; k++ ){
								  if( in.Slinks[i][k] )
								  Dist_costs[t] +=  allocDem_q[i][k][t] * in.SCost[i][k];
							  }
						  }
					  }
					  FTL_costs[t] += Math.max( ftl_costs , in.mpc[e][t] );
					  
				}
			}
			Tot_FTL_costs += FTL_costs[t];
			Tot_Dist_costs += Dist_costs[t];
			if ( in.getSeason (t) == "HIGH" )  Tot_costs_hs += FTL_costs[t] + Dist_costs[t] ;
			if ( in.getSeason (t) == "MEDIUM" )   Tot_costs_ms += FTL_costs[t] + Dist_costs[t] ;
		    if ( in.getSeason (t) == "LOW" )   Tot_costs_ls += FTL_costs[t] + Dist_costs[t] ;
		}
		
		
		String format = "%-15s |";
		System.out.println( "COSTS PER PERIOD" );
		for(int i = 0; i < 4; i++){	
			if(i==0) System.out.format( format,  "t" );
			if(i==1) System.out.format( format,  "FTL Costs" );
			if(i==2) System.out.format( format,  "Dist. Costs" );	
			if(i==3) System.out.format( format,  "Total Period" );	
			for(int t = 0; t < in.NP; t++){
				if(i==0) System.out.format( format,  t+1 );
				if(i==1) System.out.format( format,  in.round ( FTL_costs[t],2 ) );
				if(i==2) System.out.format( format,  in.round ( Dist_costs[t],2 ) );
				if(i==3) System.out.format( format,  in.round ( FTL_costs[t]+Dist_costs[t],2 ) );
			} 
			if(i==0) System.out.println( "Total" );
			if(i==1) { System.out.format( format, in.round ( Tot_FTL_costs ,1 ) )   ; System.out.println( );}
			if(i==2) { System.out.format( format, in.round ( Tot_Dist_costs ,1 ) ); System.out.println( );}
			if(i==3) { System.out.format( format,  " " ) ; System.out.println( ); }
		}
		
		
		
		String format2 = "%-25s %-18s %-30s %25s";
		double ftl_percent =  in.round( Tot_FTL_costs/ (Tot_FTL_costs+Tot_Dist_costs) *100, 1 );
		double dist_percent =  in.round( Tot_Dist_costs/ (Tot_FTL_costs+Tot_Dist_costs) *100, 1);
		double ab_min_ftl_costs = in.round( (Tot_FTL_costs - in.minCarCost)*100 / in.minCarCost, 1 );
		double ab_min_dist_costs = in.round( (Tot_Dist_costs - in.minDistCost)*100 / in.minDistCost, 1 );
		
		System.out.println( );
		System.out.format( format2, "TOTAL COSTS", "" ,"% Above min. costs", "min. costs");
		System.out.println( );
		System.out.format( format2, "FTL Costs:",  in.round ( Tot_FTL_costs ,1 ) +" ("+ ftl_percent +"%)", 
				"% Above min. FTL costs:",   in.round ( Tot_FTL_costs, 1 ) + " / " + in.round ( in.minCarCost,1 ) +" ("+ ab_min_ftl_costs +"%)" );
		System.out.println( );
		System.out.format( format2, "Dist. Costs:", in.round ( Tot_Dist_costs ,1 ) +" ("+ dist_percent +"%)",
				"% Above min. Dist. costs:", in.round ( Tot_Dist_costs, 1 ) + " / " + in.round( in.minDistCost,1 ) +" ("+ ab_min_dist_costs +"%)" );
		System.out.println( );
		System.out.format( format2, "Costs (Low Season):", in.round ( Tot_costs_ls ,1 ) , "", "");
		System.out.println( );
		System.out.format( format2, "Costs (Medium Season):", in.round ( Tot_costs_ms ,1 ) , "", "");
		System.out.println( );
		System.out.format( format2, "Costs (High Season):", in.round ( Tot_costs_hs ,1 ) , "", "");
		System.out.println( );
	}
	
	
	
	/*
	 * // print with cases :
	// case 1 = only basic values
	// case 2 = complete grid
	// case 3 = complete + carriers + MPC + Costs
	 */
	public void printOutput_1( Instance in , String method ){
		printCompleteSolution( in, method );	System.out.println( );
		printCarriersData( in );				System.out.println( );
		printMPC_UtilizationData( in );			System.out.println( );
		printCostsData( in );					System.out.println( );
	}
	
	
	// Output without tables
	public void printOutput_2( Instance in , String method ){
		printBasicSolutionValues( in, method );	System.out.println( );
		printCarriersData( in );				System.out.println( );
		printMPC_UtilizationData2( in );		System.out.println( );
		printCostsData( in );					System.out.println( );
	}
	
	

}
