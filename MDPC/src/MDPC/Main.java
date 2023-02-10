package MDPC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

//import MDPC.Benders;
import MDPC.Instance;
import ilog.concert.IloException;


public class Main {
	public static void main( String[] args ){		
		try {		
			run( "PC", args[0] );
		} catch ( Exception e ) {			
			e.printStackTrace( );
		}		
	}		
	/**
	 * Reproduce the main() method with arguments: inputFile location, Method 
	 * @throws IloException 
	 */
	public static void run( String method, String path ) { // throws IloException {
		if(method.equals( "Cluster" )){
			File nFile = new File ( path );			
			Instance in = new Instance( nFile );
			outputFile( "Cluster", in.name+"_Detailed_Results" );
			System.out.println( "::: Instance Data :::" ); System.out.println( );
			System.out.println( in.name ); 				   System.out.println(  );
			in.printInstanceData( );
			CPLX_methods cplx = new CPLX_methods( in );
			for( int m = 2; m < 5; m++ ) {
				in.setContractConditions_mct( m );
				in.printName( );
				//------- 
				cplx.solveMDPC_XCT( in , 0 );
				cplx.printOutput_1( in, "CPLEX B&C" );
				cplx.solveMDPC_XCT( in , 3 );
				cplx.printOutput_1( in, "CPLEX Benders" );
				CBA_method cba = new CBA_method( in );
				try { cba.solveMDPC( in , 0 ); }
				catch (IloException e) { e.printStackTrace(); }
				RRH_method rrh = new RRH_method( in );
				rrh.solveMDPC( in, 0 );
			}
			in.setContractConditions_mct( 1 ); 
			in.setPrices( 0.0 ); 		// increase the FTL costs by 25% more
			in.printName( );
			cplx.solveMDPC( in, 0 );
			//------- 
			in.setPrices( 0.10 ); 		// increase the FTL costs by 50% more
			in.printName( );
			cplx.solveMDPC( in, 0 );
			//------- 
			in.setPrices( 0.25 ); 		// increase the FTL costs by 25% more
			in.printName( );
			cplx.solveMDPC( in, 0 );
			//-------
			in.setContractConditions_mct( in.NP ); 
			in.setPrices( 0 ); 	// return to file values. 
			in.printName( );
			cplx.solveMDPC( in, 0 );
			//-------
			in.setContractConditions_mct( 1 );
			in.setContractConditions_mpc( 0 );
			in.printName( "MPC = 0" );
			cplx.solveMDPC( in, 0 );
			
		}else if( method.equals("PC") ){
			try {
				//Try several instances in a folder at the same time	
				Files.walk( Paths.get( path ) ).forEach ( filePath -> {					
					if( Files.isRegularFile( filePath ) ) {
						String fileName = filePath.getFileName( ).toString( );									
						if( !fileName.equals( ".DS_Store" ) ) {
							System.out.println( ":::Instance Data:::" ); System.out.println( );
							System.out.println( fileName ); System.out.println( );
						    File nFi = new File ( path + "/" + fileName );
							Instance inst = new Instance( nFi );
							inst.printInstanceData( );
							CPLX_methods cplx = new CPLX_methods( inst );
							for( int m = 2; m < 3; m++ ) {
								inst.setContractConditions_mct( m );
								inst.printName( );
								//----------
								cplx.solveMDPC( inst, 0 );
//								cplx.solveMDPC( inst, 3 );
								CBA_method cba = new CBA_method( inst );
								try { cba.solveMDPC( inst , 0 ); }
								catch (IloException e) { e.printStackTrace(); }
								RRH_method rrh = new RRH_method( inst );
//								rrh.solveMDPC( inst, 0 );
							}
							//------- 
							inst.setContractConditions_mct( 1 );
							inst.setPrices( 0.0 ); 	// increase the FTL costs by 50% more
							inst.printName( );
							cplx.solveMDPC( inst, 0 );
							//------- 
							inst.setPrices( 0.10 ); 	// increase the FTL costs by 50% more
							inst.printName( );
							cplx.solveMDPC( inst, 0 );
							//------- 
							inst.setPrices( 0.25 ); 	// increase the FTL costs by 25% more
							inst.printName( );
							cplx.solveMDPC( inst, 0 );
							//-------
							inst.setContractConditions_mct( inst.NP ); 
							inst.setPrices( 0 ); 		// return to instance values. 
							inst.printName( );
							cplx.solveMDPC( inst, 0 );
							//-------
							inst.setContractConditions_mct( 1 );
							inst.setContractConditions_mpc( 0 );
							inst.printName( "MPC = 0" );
							cplx.solveMDPC( inst, 0 );
							
						}	
					}
				} );	
			} catch (IOException e) {			
					e.printStackTrace( );
			}
		}
	}
	
	
	
	public static void outputFile( String input, String fn ){
		String oPath = "";
		if( input.equals( "PC" ) )   oPath = "C:/Users/Christian/Desktop";
		//"C:/Users/Christian/Desktop/HEC_PhD - 3RD YEAR/workspace/"+"Instances/cbndp_instances/results/";
		PrintStream output_file;
		try {
			oPath = "Results_larges_all/";
			output_file = new PrintStream( new FileOutputStream( oPath+fn ) );			
			System.setOut(output_file); 
			System.setErr(output_file);
		} catch (FileNotFoundException e) {
			e.printStackTrace( );
		}
	}
	
}
