import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

class Card {

	public int bonusActionCount;
	public int goodActionCount;
	public Player.CardType cardType;
	public int badActionCount;

	public Card(int bonusActionCount, int goodActionCount, Player.CardType cardType, int badActionCount) {
		this.bonusActionCount = bonusActionCount;
		this.goodActionCount = goodActionCount;
		this.cardType = cardType;
		this.badActionCount = badActionCount;
	}

	static Card makeCard(Player.CardType type) {
		switch (type) {
		case BONUS:
			return new Card(1, 0, type, 1);
		case TECHNICAL_DEBT:
			return new Card(0, 0, type, 0);
		default:
			return new Card(0, 2, type, 2);
		}

	}
}

class ReleaseCandidate {
	public Application app;
	public int technicalDebtCost;
	public int[] missingResources;

	public static ReleaseCandidate calculateReleaseCandidate(Application app, int[] cardsInHand) {
		ReleaseCandidate rc = new ReleaseCandidate();
		rc.app = app;
		rc.technicalDebtCost = app.calculatTechnicalDebt(cardsInHand);
		rc.missingResources = app.calculateMissingResources(cardsInHand);
		return rc;
	}

}

class Application {
	public int id;
	public int[] resources = new int[8];

	public Application(String data) {
		String[] applicationDetails = data.split(" ");
		id = Integer.parseInt(applicationDetails[1]);
		for (int resourceId = 2; resourceId < applicationDetails.length; ++resourceId) {
			resources[resourceId - 2] = Integer.parseInt(applicationDetails[resourceId]);
		}
	}

	public String toString() {
		Player.CardType[] values = Player.CardType.values();
		String result = String.format("(%d) ", id);
		for (int i = 0; i < resources.length; i++) {
			result += String.format("%d %s, ", resources[i], values[i].name().substring(0, 5));
		}
		return result;
	}

	public int calculatTechnicalDebt(int[] cardsInHand) {
		int nbTechnicalDebts = 0;
		for (int res = 0; res < Player.RESOURCES_COUNT; res++) {
			nbTechnicalDebts += Math.max(0, this.resources[res] - cardsInHand[res]);
		}
		// bonuses can remove 1 technical debt each
		nbTechnicalDebts = Math.max(0, nbTechnicalDebts - cardsInHand[Player.CardType.BONUS.ordinal()]);

		return nbTechnicalDebts;
	}
	
	public int[] calculateMissingResources(int[] cardsInHand) {
		
		int[] missingResources = new int[Player.RESOURCES_COUNT];
		
		for (int res = 0; res < Player.RESOURCES_COUNT; res++) {
			missingResources[res] = Math.max(0, this.resources[res] - cardsInHand[res]);
		}
		
		return missingResources;
	}
}

class Player {
	enum CardType {
		TRAINING, CODING, DAILY_ROUTINE, TASK_PRIORITIZATION, ARCHITECTURE_STUDY, CONTINUOUS_INTEGRATION, CODE_REVIEW,
		REFACTORING, BONUS, TECHNICAL_DEBT
	}

	static int ZONES_COUNT = 8;
	static int RESOURCES_COUNT = 8;
	static final String MOVE_PHASE = "MOVE";
	static final String GIVE_PHASE = "GIVE_CARD";
	static final String THROW_PHASE = "THROW_CARD";
	static final String PLAY_PHASE = "PLAY_CARD";
	static final String RELEASE_PHASE = "RELEASE";

	static ReleaseCandidate findBestRelease(int[] myCardsInHand, List<ReleaseCandidate> releaseCandidates) {

		assert releaseCandidates.size() > 0 : "No application candidates for best release";

		// max technical debt we can generate with our hand, depends on the type of card
		int maxDebtInHand = Arrays.stream(myCardsInHand).reduce(0, (sum, card) -> {
			switch (CardType.values()[card]) {
			case TECHNICAL_DEBT:
				return sum; // doesn't count
			case BONUS:
				return sum + 1;
			default:
				return sum + 2;
			}
		});

		int bestAppId = 0;
		int bestDebt = Integer.MAX_VALUE;

		// calculate debts for every app and keep track of the best
		for (int app = 0; app < releaseCandidates.size(); app++) {
			// count potential technical debts with no bonus
			int nbTechnicalDebts = releaseCandidates.get(app).technicalDebtCost;
			// can we generate that much debt ?
			if (nbTechnicalDebts <= maxDebtInHand && nbTechnicalDebts < bestDebt) { // new best candidate
				bestAppId = app;
				bestDebt = nbTechnicalDebts;
			}
		}

		return releaseCandidates.get(bestAppId);

	}
	
	static CardType findBestZoneMiddle(List<Application> applications, int[] cardsInDiscard) {
		//compare the resource needed for all applications to the resource in hand to find the most needed one.
		assert cardsInDiscard.length == RESOURCES_COUNT+2: "carsInDraw has the wrong element number";
		int[] applicationsResourceSum = new int[RESOURCES_COUNT];
		
		System.err.println(Arrays.toString(cardsInDiscard));
		
		applicationsResourceSum = applications.stream().reduce(
			new int[RESOURCES_COUNT],
			(BiFunction<int[], ? super Application, int[]>) (resourceSum, app) -> {
				int[] result = new int[RESOURCES_COUNT];
				for(int i = 0; i < RESOURCES_COUNT; i++) {
					result[i] = resourceSum[i] + app.resources[i];
				}
				return result;
			},
			(BinaryOperator<int[]>) (sumA, sumB) -> {
				int[] result = new int[RESOURCES_COUNT];
				for(int i = 0; i < RESOURCES_COUNT; i++) {
					result[i] = sumA[i] + sumB[i];
				}
				return result;
		});
		System.err.println(Arrays.toString(applicationsResourceSum));
		
		int resourceNeededIndex = 0;
		int maxResourceNeed = 0;
		for(int i = 0; i < RESOURCES_COUNT; i++) {
			int resourceDiff = applicationsResourceSum[i] - cardsInDiscard[i];
			if(resourceDiff > maxResourceNeed) {
				resourceNeededIndex = i;
				maxResourceNeed =  resourceDiff;
			}
		}
	
		return CardType.values()[resourceNeededIndex];
	}
	
	static CardType findBestZoneEndGame(List<Application> applications, int[] cardsInDiscard)  {
		return findBestZoneMiddle(applications, cardsInDiscard);
		
	}
	
	static CardType findBestZone(int score, List<Application> applications, int[] cardsInDiscard) {
		if(score < 4) return findBestZoneMiddle(applications, cardsInDiscard);
		else return findBestZoneEndGame(applications, cardsInDiscard);
		
	}

	static int turn = 0;

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);

		while (true) {
			// read game phase
			String gamePhase = scanner.nextLine();

			// read applications details
			int applicationsCount = Integer.parseInt(scanner.nextLine());
			HashMap<Integer, Application> applications = new HashMap<Integer, Application>();
			for (int i = 0; i < applicationsCount; ++i) {
				String applicationData = scanner.nextLine();
				Application app = new Application(applicationData);
				applications.put(app.id, app);
			}

			// read players details
			String[] playerDetails = scanner.nextLine().split(" ");
			int myLocation = Integer.parseInt(playerDetails[0]);
			int myScore = Integer.parseInt(playerDetails[1]);
			int myPermanentDailyRoutineCards = Integer.parseInt(playerDetails[2]);
			int myPermanentArchitectureStudyCards = Integer.parseInt(playerDetails[3]);

			playerDetails = scanner.nextLine().split(" ");
			int opponentLocation = Integer.parseInt(playerDetails[0]);
			int opponentScore = Integer.parseInt(playerDetails[1]);
			int opponentPermanentDailyRoutineCards = Integer.parseInt(playerDetails[2]);
			int opponentPermanentArchitectureStudyCards = Integer.parseInt(playerDetails[3]);

			// read player cards
			int[] myCardsInHand = new int[10];
			int[] myDrawPile = new int[10];
			int[] myDiscardPile = new int[10];
			int[] myAutomatedCards = new int[10];

			int cardLocationsCount = Integer.parseInt(scanner.nextLine());
			for (int i = 0; i < cardLocationsCount; i++) {
				String cardsData = scanner.nextLine();
				String[] cardsDetails = cardsData.split(" ");
				int[] cards = new int[10];
				String cardsLocation = cardsDetails[0]; // the location of the card list. It can be HAND, DRAW, DISCARD
														// or OPPONENT_CARDS (AUTOMATED and OPPONENT_AUTOMATED will
														// appear in later leagues)
				if (cardsLocation.equals("HAND")) {
					cards = myCardsInHand;
				} else if (cardsLocation.equals("DRAW")) {
					cards = myDrawPile;
				} else if (cardsLocation.equals("DISCARD")) {
					cards = myDiscardPile;
				} else if (cardsLocation.equals("AUTOMATED")) {
					cards = myAutomatedCards;
				}
				for (int j = 1; j < cardsDetails.length; ++j) {
					cards[j - 1] = Integer.parseInt(cardsDetails[j]);
				}
			}

			// read possible moves
			// all types of move including releases, wait etc
			int movesCount = Integer.parseInt(scanner.nextLine());
			String[] moves = new String[movesCount];
			List<ReleaseCandidate> releaseCandidates = new ArrayList<ReleaseCandidate>();
			for (int i = 0; i < movesCount; ++i) {
				String move = scanner.nextLine();
				moves[i] = move;
				String[] moveData = move.split(" ");

				if (moveData[0].equals("RELEASE")) {
					releaseCandidates.add(ReleaseCandidate
							.calculateReleaseCandidate(applications.get(Integer.parseInt(moveData[1])), myCardsInHand));
				}
			}

			switch (gamePhase) {
			case MOVE_PHASE:
				turn++;
				
				CardType bestZone = findBestZone(myScore, new ArrayList<Application>(applications.values()), myDiscardPile);
				int index = bestZone.ordinal();
				if(index == myLocation) {
					System.out.println("RANDOM");
					break;
				}
				System.out.println(String.format("MOVE %d", index));
				break;
			case GIVE_PHASE:
				if(myCardsInHand[CardType.BONUS.ordinal()] > 0) {
					System.out.println(String.format("GIVE %d", CardType.BONUS.ordinal()));
					break;
				}
				System.out.println("RANDOM");
				break;
			case THROW_PHASE:
				// will appear in Bronze League
				System.out.println("RANDOM");
				break;
			case PLAY_PHASE:
				if(myCardsInHand[CardType.ARCHITECTURE_STUDY.ordinal()] > 0) {
					System.out.println("ARCHITECTURE_STUDY");
					break;
				}
				System.out.println("WAIT");
				break;
			case RELEASE_PHASE:
				System.err.println("RELEASE PHASE");

				if (releaseCandidates.size() == 0) {
					System.out.println("WAIT");
					break;
				}
				ReleaseCandidate candidate = findBestRelease(myCardsInHand, releaseCandidates);
				if (candidate.technicalDebtCost > 3 && myScore < 4) {
					System.out.println("WAIT");
					break;
				}
				System.out.println(String.format("RELEASE %d", candidate.app.id));
				break;
			default:
				System.out.println("RANDOM");
				break;
			}
		}
	}
}