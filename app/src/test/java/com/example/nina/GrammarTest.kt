package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchMorphologyTest {

    private fun conj(verb: String, person: Person) = FrenchMorphology.conjugate(verb, person)

    @Test
    fun firstGroup_withSpellingChanges() {
        assertEquals("mange", conj("manger", Person.JE))
        assertEquals("mangeons", conj("manger", Person.NOUS))
        assertEquals("commençons", conj("commencer", Person.NOUS))
        assertEquals("lève", conj("lever", Person.JE))
        assertEquals("achète", conj("acheter", Person.IL))
        assertEquals("appelle", conj("appeler", Person.JE))
        assertEquals("appelons", conj("appeler", Person.NOUS))
        assertEquals("préfèrent", conj("préférer", Person.ILS))
        assertEquals("nettoie", conj("nettoyer", Person.JE))
        assertEquals("jouez", conj("jouer", Person.VOUS))
        assertEquals("mangé", FrenchMorphology.pastParticiple("manger"))
    }

    @Test
    fun otherGroups_andIrregulars() {
        assertEquals("finissons", conj("finir", Person.NOUS))
        assertEquals("veux", conj("vouloir", Person.JE))
        assertEquals("vont", conj("aller", Person.ILS))
        assertEquals("apprennent", conj("apprendre", Person.ILS))
        assertEquals("reviens", conj("revenir", Person.JE))
        assertEquals("reçois", conj("recevoir", Person.JE))
        assertEquals("ouvre", conj("ouvrir", Person.JE))
        assertEquals("ouvert", FrenchMorphology.pastParticiple("ouvrir"))
        assertEquals("attend", conj("attendre", Person.IL))
        assertEquals("écrivons", conj("écrire", Person.NOUS))
        assertEquals("sourit", conj("sourire", Person.IL))
        assertEquals("conduisons", conj("conduire", Person.NOUS))
        assertEquals("dort", conj("dormir", Person.IL))
    }

    @Test
    fun reflexiveVerbs() {
        assertEquals("me lave", conj("se laver", Person.JE))
        assertEquals("t'habilles", conj("s'habiller", Person.TU))
        assertEquals("nous couchons", conj("se coucher", Person.NOUS))
    }

    @Test
    fun nounsAreNotVerbs() {
        listOf("cahier", "papier", "miroir", "soir", "rocher", "maison", "hiver").forEach {
            assertFalse("$it n'est pas un verbe", FrenchMorphology.isVerb(it))
        }
        listOf("crier", "manger", "voir", "finir", "boire", "se laver").forEach {
            assertTrue("$it est un verbe", FrenchMorphology.isVerb(it))
        }
    }

    @Test
    fun plurals() {
        assertEquals("chevaux", FrenchMorphology.plural("cheval"))
        assertEquals("gâteaux", FrenchMorphology.plural("gâteau"))
        assertEquals("jeux", FrenchMorphology.plural("jeu"))
        assertEquals("choux", FrenchMorphology.plural("chou"))
        assertEquals("pneus", FrenchMorphology.plural("pneu"))
        assertEquals("souris", FrenchMorphology.plural("souris"))
        assertEquals("chats", FrenchMorphology.plural("chat"))
    }

    @Test
    fun wordForms() {
        val verb = FrenchMorphology.wordForms("Manger")
        assertEquals(listOf("manger", "mange", "manges", "mangeons", "mangez", "mangent", "mangé"), verb.map { it.text })
        assertEquals("je, il", verb[1].hint)

        assertEquals(listOf("chat", "chats"), FrenchMorphology.wordForms("Chat").map { it.text })
        assertTrue(FrenchMorphology.wordForms("Encore").isEmpty())
        assertTrue(FrenchMorphology.wordForms("Moi").isEmpty())
    }
}

class LocalGrammarEngineTest {

    private fun refine(s: String) = LocalGrammarEngine.refine(s)

    @Test
    fun conjugatesFirstVerbAfterSubject() {
        assertEquals("Je veux manger", refine("Moi Vouloir Manger"))
        assertEquals("Je mange pomme", refine("moi manger pomme"))
        assertEquals("Tu vas école", refine("toi aller école"))
        assertEquals("Nous mangeons gâteau", refine("nous manger gâteau"))
        assertEquals("Ils veulent dormir", refine("ils vouloir dormir"))
        assertEquals("Maman va maison", refine("Maman aller maison"))
    }

    @Test
    fun negationAndReflexive() {
        assertEquals("Je n'aime pas", refine("moi pas aimer"))
        assertEquals("Je ne veux pas manger", refine("moi pas vouloir manger"))
        assertEquals("Je me lave", refine("moi se laver"))
        assertEquals("Je m'habille", refine("moi s'habiller"))
    }

    @Test
    fun elisionsAndContractions() {
        assertEquals("J'aime", refine("moi aimer"))
        assertEquals("Je bois de l'eau", refine("moi boire de le eau"))
        assertEquals("Aller au parc", refine("aller à le parc"))
    }

    @Test
    fun leavesOtherWordsAlone() {
        assertEquals("Jouer avec moi", refine("jouer avec moi"))
        assertEquals("Je veux manger", refine("je veux manger")) // Déjà conjugué (formes du mot)
        assertEquals("Encore", refine("Encore"))
        assertEquals("Encore manger", refine("encore manger"))
        assertEquals("", refine("  "))
    }
}
