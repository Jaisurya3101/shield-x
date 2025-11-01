# src/models/harassment.py

from typing import List, Dict, Any
from transformers import pipeline
import torch
import logging

logger = logging.getLogger(__name__)


class HarassmentDetector:
    def __init__(self):
        """
        DeepGuard Contextual + Intent-Aware Harassment Detector
        Detects harassment, threats, stalking, manipulation, coercion, and hate speech.
        """
        logger.info("🧠 Initializing DeepGuard Contextual AI Harassment Detector...")
        self.models = {}
        self.active_models = []

        # ==============================================================
        # Load contextual transformer models (ensemble approach)
        # ==============================================================
        model_configs = {
            "toxic_bert": "unitary/toxic-bert",
            "roberta_toxicity": "s-nlp/roberta_toxicity_classifier",
            "deberta_toxic": "unitary/unbiased-toxic-roberta",
        }

        for name, model_name in model_configs.items():
            try:
                logger.info(f"→ Loading model: {model_name}")
                self.models[name] = pipeline(
                    "text-classification",
                    model=model_name,
                    return_all_scores=True,
                    truncation=True,
                    max_length=512,
                    device=0 if torch.cuda.is_available() else -1,
                )
                self.active_models.append(name)
                logger.info(f"✅ Loaded {name}")
            except Exception as e:
                logger.warning(f"⚠️ Failed to load {model_name}: {e}")

        # ==============================================================
        # Fallback configuration
        # ==============================================================
        self.keyword_fallback = not bool(self.active_models)
        if self.keyword_fallback:
            logger.warning("⚠️ No AI models loaded — using keyword fallback only.")
        else:
            logger.info(f"✅ Loaded contextual models: {self.active_models}")

        # ==============================================================
        # Keyword & intent data
        # ==============================================================
        self.fallback_words = [
            "kill", "hate", "follow", "watch", "control", "obey", "attack",
            "destroy", "bitch", "threat", "blackmail", "force", "hurt",
            "find you", "track you", "waiting outside", "ruin", "break you",
            "die", "death", "murder", "stalk", "creep", "loser", "worthless"
        ]

        self.intent_triggers = [
            "follow you", "find you", "track you", "watch you",
            "hurt you", "kill you", "destroy you", "make you pay",
            "no one will believe you", "i'm outside", "waiting for you",
            "you can't escape", "i know where you live", "i'll get you",
            "you will regret", "you deserve this", "you will die",
            "i'm watching", "i see you", "found you"
        ]

        self.category_map = {
            "threat": ["kill", "hurt", "destroy", "attack", "revenge", "make you pay", 
                      "you will regret", "die", "death", "murder", "harm"],
            "stalking": ["follow", "find you", "track", "watch", "waiting outside", 
                        "outside your house", "i see you", "i'm watching", "found you"],
            "manipulation": ["control", "obey", "no one will believe you", "you owe me", 
                           "you can't escape", "you're mine", "you belong to me"],
            "harassment": ["bitch", "hate", "worthless", "idiot", "stupid", "pathetic", 
                          "trash", "loser", "ugly", "disgusting"],
            "coercion": ["blackmail", "force", "you must", "you have to", "submit", 
                        "do what i say", "or else"]
        }

    def analyze_text(self, text: str) -> Dict[str, Any]:
        """Analyze text for harassment, threat, stalking, coercion, etc."""
        if not text or not text.strip():
            return {
                "is_harassment": False,
                "confidence": 0.0,
                "severity": "safe",
                "category": "empty",
                "method": "none",
                "error": "Empty text input"
            }

        text_lower = text.lower()

        # ---------- Contextual AI detection ----------
        if not self.keyword_fallback:
            ai_scores, model_outputs = [], {}

            for name, model in self.models.items():
                try:
                    outputs = model(text)
                    if outputs and isinstance(outputs, list) and len(outputs) > 0:
                        label_scores = {r['label'].lower(): r['score'] for r in outputs[0]}
                        toxic_score = self._extract_toxic_score(label_scores)
                        model_outputs[name] = label_scores
                        ai_scores.append(toxic_score)
                        logger.debug(f"Model {name} score: {toxic_score:.4f}")
                except Exception as e:
                    logger.warning(f"⚠️ Model {name} failed: {e}")

            if ai_scores:
                avg_toxic = float(sum(ai_scores) / len(ai_scores))

                # Intent Amplification
                intent_boost = 0.0
                matched_intents = [t for t in self.intent_triggers if t in text_lower]
                if matched_intents:
                    intent_boost = min(0.35, 0.15 * len(matched_intents))
                    avg_toxic = min(1.0, avg_toxic + intent_boost)
                    logger.debug(f"Intent boost: +{intent_boost:.2f} for {len(matched_intents)} triggers")

                # Variance smoothing
                if len(ai_scores) > 1:
                    variance = max(ai_scores) - min(ai_scores)
                    if variance > 0.4:
                        avg_toxic *= 0.9
                        logger.debug(f"Applied variance smoothing: {variance:.2f}")

                severity = self._classify_severity(avg_toxic)
                category = self._detect_category(text_lower)

                # Final decision
                is_harassment = (
                    avg_toxic >= 0.35
                    or bool(matched_intents)
                    or self._has_critical_keywords(text_lower)
                )

                return {
                    "is_harassment": is_harassment,
                    "confidence": round(avg_toxic, 4),
                    "severity": severity,
                    "category": category,
                    "method": "contextual_ai",
                    "models_used": self.active_models,
                    "intent_triggers": matched_intents if matched_intents else None,
                    "details": model_outputs
                }

        # Keyword fallback
        return self._keyword_fallback_analysis(text)

    def _extract_toxic_score(self, label_scores: Dict[str, float]) -> float:
        """Extract toxicity score from various model label formats"""
        if "toxic" in label_scores:
            return label_scores["toxic"]
        elif "label_1" in label_scores:
            return label_scores["label_1"]
        elif "toxicity" in label_scores:
            return label_scores["toxicity"]
        elif "non_toxic" in label_scores:
            return 1 - label_scores["non_toxic"]
        elif "label_0" in label_scores:
            return 1 - label_scores["label_0"]
        return sum(label_scores.values()) / len(label_scores) if label_scores else 0.0

    def _has_critical_keywords(self, text_lower: str) -> bool:
        """Check if text contains critical harassment keywords"""
        critical_words = ["kill", "die", "death", "murder", "hurt", "destroy", "attack"]
        return any(word in text_lower for word in critical_words)

    def _keyword_fallback_analysis(self, text: str) -> Dict[str, Any]:
        """Fallback keyword-based detection when AI models unavailable"""
        text_lower = text.lower()
        found = [word for word in self.fallback_words if word in text_lower]
        intent_found = [t for t in self.intent_triggers if t in text_lower]
        
        base_score = 0.3 if found else 0.0
        keyword_boost = min(0.4, 0.1 * len(found))
        intent_boost = min(0.3, 0.15 * len(intent_found))
        score = min(0.95, base_score + keyword_boost + intent_boost)
        severity = self._classify_severity(score)
        category = self._detect_category(text_lower)

        return {
            "is_harassment": bool(found or intent_found),
            "confidence": round(score, 4),
            "severity": severity,
            "category": category,
            "method": "keyword_fallback",
            "found_keywords": found if found else None,
            "intent_triggers": intent_found if intent_found else None
        }

    def detect_harassment(self, texts: List[str]) -> List[Dict[str, Any]]:
        """Batch analyze multiple messages."""
        results = []
        for text in texts:
            analysis = self.analyze_text(text)
            formatted = {
                "TOXIC": analysis.get("confidence", 0.0) if analysis.get("is_harassment") else 0.0,
                "SEVERE_TOXIC": analysis.get("confidence", 0.0) if analysis.get("severity") in ["critical", "high"] else 0.0,
                "THREAT": analysis.get("confidence", 0.0) if analysis.get("category") == "threat" else 0.0,
                "OBSCENE": analysis.get("confidence", 0.0) if analysis.get("category") == "harassment" else 0.0,
                "INSULT": analysis.get("confidence", 0.0) if "harassment" in analysis.get("category", "") else 0.0,
                "_analysis": analysis
            }
            results.append(formatted)
        return results

    def _classify_severity(self, score: float) -> str:
        """Classify severity level based on confidence score"""
        if score >= 0.85:
            return "critical"
        elif score >= 0.65:
            return "high"
        elif score >= 0.45:
            return "medium"
        elif score >= 0.25:
            return "low"
        return "safe"

    def _detect_category(self, text_lower: str) -> str:
        """Detect primary harassment category"""
        category_scores = {}
        for category, keywords in self.category_map.items():
            matches = sum(1 for k in keywords if k in text_lower)
            if matches > 0:
                category_scores[category] = matches
        if category_scores:
            return max(category_scores, key=category_scores.get)
        return "general"

    def get_model_info(self) -> Dict[str, Any]:
        """Get information about loaded models"""
        return {
            "active_models": self.active_models,
            "fallback_mode": self.keyword_fallback,
            "model_count": len(self.active_models),
            "device": "cuda" if torch.cuda.is_available() else "cpu"
        }

    def is_healthy(self) -> bool:
        """Check if detector is ready to analyze"""
        return len(self.active_models) > 0 or self.keyword_fallback