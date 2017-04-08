package com.oakinvest.b2g.batch.bitcoin;

import com.oakinvest.b2g.batch.bitcoin.step1.BitcoinBatchBlocks;
import com.oakinvest.b2g.batch.bitcoin.step2.BitcoinBatchAddresses;
import com.oakinvest.b2g.batch.bitcoin.step3.BitcoinBatchTransactions;
import com.oakinvest.b2g.batch.bitcoin.step4.BitcoinBatchRelations;
import com.oakinvest.b2g.domain.bitcoin.BitcoinBlockState;
import com.oakinvest.b2g.repository.bitcoin.BitcoinBlockRepository;
import com.oakinvest.b2g.service.StatusService;
import com.oakinvest.b2g.service.bitcoin.BitcoinStatisticService;
import com.oakinvest.b2g.service.ext.bitcoin.bitcoind.BitcoindService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Temporary batch used because multi threading doesn't work.
 * Created by straumat on 02/03/17.
 */
@Component
public class BitcoinBatch {

	/**
	 * How many milli seconds in one second.
	 */
	private static final float MILLISECONDS_IN_SECONDS = 1000F;

	/**
	 * How much time it takes to create a new block for bitcoin (10 minutes).
	 */
	private static final int TIME_BEFORE_A_NEW_BITCOIN_BLOCK = 10 * 60 * 1000;

	/**
	 * Pause between imports.
	 */
	private static final int PAUSE_BETWEEN_IMPORTS = 100;

	/**
	 * Initial delay before imports.
	 */
	private static final int INITIAL_DELAY_BEFORE_IMPORTS = 10 * 1000;

	/**
	 * Logger.
	 */
	private final Logger log = LoggerFactory.getLogger(BitcoinBatch.class);

	/**
	 * Import batch.
	 */
	@Autowired
	private BitcoinBatchBlocks batchBlocks;

	/**
	 * Import batch.
	 */
	@Autowired
	private BitcoinBatchAddresses batchAddresses;

	/**
	 * Import batch.
	 */
	@Autowired
	private BitcoinBatchTransactions batchTransactions;

	/**
	 * Import batch.
	 */
	@Autowired
	private BitcoinBatchRelations batchRelations;

	/**
	 * Status service.
	 */
	@Autowired
	private StatusService status;

	/**
	 * Bitcoin statistic service.
	 */
	@Autowired
	private BitcoinStatisticService bitcoinStatisticService;

	/**
	 * Bitcoin block repository.
	 */
	@Autowired
	private BitcoinBlockRepository blockRepository;

	/**
	 * Bitcoind service.
	 */
	@Autowired
	private BitcoindService bitcoindService;

	/**
	 * Import data.
	 */
	@Scheduled(fixedDelay = PAUSE_BETWEEN_IMPORTS, initialDelay = INITIAL_DELAY_BEFORE_IMPORTS)
	@SuppressWarnings("checkstyle:designforextension")
	public void importData() {
		// Update block statistics.
		long importedBlockCount = blockRepository.countBlockByState(BitcoinBlockState.IMPORTED);
		long totalBlockCount = bitcoindService.getBlockCount().getResult();

		// Update status.
		status.setImportedBlockCount(importedBlockCount);
		if (totalBlockCount != status.getTotalBlockCount()) {
			status.setTotalBlockCount(totalBlockCount);
		}

		// Make a pause if there is nothing to do (If we are up to date with the blockchain last block)
		if (importedBlockCount == totalBlockCount) {
			try {
				Thread.sleep(TIME_BEFORE_A_NEW_BITCOIN_BLOCK);
			} catch (InterruptedException e) {
				log.error("Error while pause : " + e.getMessage());
			}
		} else {
			// Importing the next available block.
			final long start = System.currentTimeMillis();
			try {
				batchBlocks.process();
				batchAddresses.process();
				batchTransactions.process();
				batchRelations.process();
			} catch (Exception e) {
				status.addError("Error in the batch processes : " + e.getMessage());
				log.error("Error in main batch : " + Arrays.toString(e.getStackTrace()));
			}

			// Adding a statistic on duration.
			bitcoinStatisticService.addBlockImportDuration((System.currentTimeMillis() - start) / MILLISECONDS_IN_SECONDS);
		}
	}

}
