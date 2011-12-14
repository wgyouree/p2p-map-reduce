package edu.gatech.cs7210.p2pmapreduce;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import de.uniba.wiai.lspi.chord.data.URL;
import edu.gatech.cs7210.p2pmapreduce.comm.CommandDispatcher;
import edu.gatech.cs7210.p2pmapreduce.comm.CommandListener;
import edu.gatech.cs7210.p2pmapreduce.comm.requests.JoinRequest;
import edu.gatech.cs7210.p2pmapreduce.node.ChordNode;
import edu.gatech.cs7210.p2pmapreduce.node.MasterNode;
import edu.gatech.cs7210.p2pmapreduce.node.MasterNode.MasterType;
import edu.gatech.cs7210.p2pmapreduce.node.SlaveNode;
import edu.gatech.cs7210.p2pmapreduce.node.SlaveNode.SlaveType;

public class P2PMapReduce {
	
	private static final String PROPERTIES_FILE = "app.properties";
	
	public void startHadoopMaster(MasterType type) {
		ApplicationContext.getInstance().setNode(new MasterNode(type));
		runAsMaster(ApplicationContext.getInstance().getUrl());
	}
	
	private void runAsMaster(URL url) {
		CommandListener listener = new CommandListener();
		listener.listen(url);
		ApplicationContext.getInstance().getNode().run();
	}
	
	private void runAsSlave(URL url) {
		CommandDispatcher dispatcher = new CommandDispatcher();
		dispatcher.dispatch(new JoinRequest(url));
		ApplicationContext.getInstance().getNode().run();
	}
	
	public void startHadoopSlave(SlaveType type) {
		ApplicationContext.getInstance().setNode(new SlaveNode(type));
		runAsSlave(ApplicationContext.getInstance().getUrl());
	}

	private static void configure(String propertiesFile) {
		try {
			Properties config = new Properties();
			File configFile = new File(propertiesFile);
			config.load(new FileInputStream(configFile));
			
			ApplicationContext appContext = ApplicationContext.getInstance();
			appContext.setPort(Integer.parseInt(config.getProperty("port")));
			appContext.setBootstrapUrl(config.getProperty("bootstrap_url"));
			appContext.setFirstNode(config.getProperty("first_node"));
			appContext.setNodeType(config.getProperty("node_type"));
			appContext.setConfigDir(config.getProperty("config_dir"));
			appContext.setBinDir(config.getProperty("bin_dir"));
			appContext.setSlaveConfigFile(config.getProperty("slave_config_file"));
			
			
		} catch (Exception e) {
			System.err.println("Failed to load properties file [" + propertiesFile + "]");
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		
		ApplicationContext.getInstance().setProtocol(URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL));
		
		P2PMapReduce p2pMapReduce = new P2PMapReduce();
		
		// create Options object
		Options options = new Options();

		// add options
		options.addOption("propertiesFile", true, "location of the properties file");
		
		// parse the options
		CommandLineParser parser = new PosixParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			
			ChordNode node = new ChordNode();
			
			String propertiesFile = cmd.getOptionValue("propertiesFile");
			if (propertiesFile == null) {
				propertiesFile = PROPERTIES_FILE;
			}
			configure(propertiesFile);
			ApplicationContext appContext = ApplicationContext.getInstance();
			
			if (!appContext.isFirstNode() && appContext.getBootstrapUrl() == null) {
				System.err.println("One of firstNode or bootstrap Url must be specified");
				System.exit(-1);
			}
			
			if (appContext.getUrl() == null) {
				System.err.println("Must specifiy port");
				System.exit(-1);
			}
			
			if (appContext.isFirstNode()) {
				appContext.setFirstNode(true);
				System.out.println("Starting ChordNode as first node in chord topology");
				node.runAsFirst();
			} else {
				node.run(appContext.getUrl());
			}
			
			if (appContext.isMaster()) {
				p2pMapReduce.startHadoopMaster(appContext.getMasterType());
			} else if (appContext.isSlave()) {
				p2pMapReduce.startHadoopSlave(appContext.getSlaveType());
			} else {
				System.err.println("Node type not recognized");
				System.exit(-1);
			}
		} catch (Exception e) {
			System.err.println("Failed to parse arugments");
		}
	}

}
