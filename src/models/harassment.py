from typing import List, Dict
from transformers import pipeline
import torch
import re


class HarassmentDetector:
    def __init__(self):
        """
        DeepGuard Contextual + Intent-Aware Harassment Detector
        Detects harassment, threats, stalking, manipulation, coercion, and hate speech.
        """
        print("🧠 Initializing DeepGuard Contextual AI Harassment Detector...")
        self.models = {}
        self.active_models = []

        # ==============================================================
        # Load contextual transformer models (ensemble approach)
        # ==============================================================
        model_configs = {
            "toxic_bert": "unitary/toxic-bert",
            "roberta_toxicity": "s-nlp/roberta_toxicity_classifier",
            "deberta_toxic": "tomh/toxic-deberta",
        }

        for name, model_name in model_configs.items():
            try:
                print(f"→ Loading model: {model_name}")
                self.models[name] = pipeline(
                    "text-classification",
                    model=model_name,
                    return_all_scores=True,
                    truncation=True,
                    from_pt=True
                )
                self.active_models.append(name)
            except Exception as e:
                print(f"⚠️ Failed to load {model_name}: {e}")

        # ==============================================================
        # Fallback configuration
        # ==============================================================
        self.keyword_fallback = not bool(self.active_models)
        if self.keyword_fallback:
            print("⚠️ No AI models loaded — using keyword fallback only.")
        else:
            print(f"✅ Loaded contextual models: {self.active_models}")

        # ==============================================================
        # Keyword & intent data
        # ==============================================================
        self.fallback_words = [
            "kill", "hate", "follow", "watch", "control", "obey", "attack",
            "destroy", "bitch", "threat", "blackmail", "force", "hurt",
            "find you", "track you", "waiting outside", "ruin", "break you"
        ]

        self.intent_triggers = [
            "follow you", "find you", "track you", "watch you",
            "hurt you", "kill you", "destroy you", "make you pay",
            "no one will believe you", "i’m outside", "waiting for you",
            "you can't escape", "i know where you live", "i’ll get you",
            "you will regret", "you deserve this", "you will die"
        ]

        self.category_map = {
            "threat": ["kill", "hurt", "destroy", "attack", "revenge", "make you pay", "you will regret", "die"],
            "stalking": ["follow", "find you", "track", "watch", "waiting outside", "outside your house"],
            "manipulation": ["control", "obey", "no one will believe you", "you owe me", "you can't escape"],
            "harassment": ["bitch", "hate", "worthless", "idiot", "stupid", "pathetic", "trash"],
            "coercion": ["blackmail", "force", "you must", "you have to", "submit"]
        }

    # ==============================================================
    # MAIN DETECTION LOGIC
    # ==============================================================
    def analyze_text(self, text: str) -> Dict[str, float]:
        """Analyze text for harassment, threat, stalking, coercion, etc."""
        if not text.strip():
            return {"error": "Empty text input"}

        text_lower = text.lower()

        # ---------- Contextual AI detection ----------
        if not self.keyword_fallback:
            ai_scores, model_outputs = [], {}

            for name, model in self.models.items():
                try:
                    outputs = model(text)
                    if outputs and isinstance(outputs, list):
                        # Convert model outputs to readable format
                        label_scores = {r['label'].lower(): r['score'] for r in outputs[0]}

                        # Handle model label differences
                        toxic_score = (
                            label_scores.get("toxic", 0.0)
                            or label_scores.get("LABEL_1", 0.0)
                            or (1 - label_scores.get("non_toxic", 0.0))
                        )

                        model_outputs[name] = label_scores
                        ai_scores.append(toxic_score)
                except Exception as e:
                    print(f"⚠️ Model {name} failed: {e}")

            if ai_scores:
                avg_toxic = float(sum(ai_scores) / len(ai_scores))

                # ---------- Intent Amplification ----------
                if any(trigger in text_lower for trigger in self.intent_triggers):
                    avg_toxic = min(1.0, avg_toxic + 0.35)

                # ---------- Variance smoothing ----------
                if len(ai_scores) > 1:
                    variance = max(ai_scores) - min(ai_scores)
                    if variance > 0.4:
                        avg_toxic *= 0.9

                severity = self._classify_severity(avg_toxic)
                category = self._detect_category(text_lower)

                # ---------- Final decision ----------
                is_harassment = (
                    avg_toxic >= 0.35
                    or any(t in text_lower for t in self.intent_triggers)
                    or any(k in text_lower for k in self.fallback_words)
                )

                return {
                    "is_harassment": is_harassment,
                    "confidence": round(avg_toxic, 4),
                    "severity": severity,
                    "category": category,
                    "method": "contextual_ai",
                    "models_used": self.active_models,
                    "details": model_outputs
                }

        # ---------- Keyword fallback ----------
        return self._keyword_fallback_analysis(text)

    # ==============================================================
    # FALLBACK DETECTION
    # ==============================================================
    def _keyword_fallback_analysis(self, text: str) -> Dict[str, float]:
        text_lower = text.lower()
        found = [word for word in self.fallback_words if word in text_lower]
        score = min(0.95, 0.3 + (0.1 * len(found))) if found else 0.0
        severity = self._classify_severity(score)
        category = self._detect_category(text_lower)

        return {
            "is_harassment": bool(found),
            "confidence": round(score, 4),
            "severity": severity,
            "category": category,
            "method": "keyword_fallback",
            "found_keywords": found
        }

    # ==============================================================
    # BULK MODE
    # ==============================================================
    def detect_harassment(self, texts: List[str]) -> List[Dict[str, float]]:
        """Batch analyze multiple messages."""
        return [self.analyze_text(t) for t in texts]

    # ==============================================================
    # HELPERS
    # ==============================================================
    def _classify_severity(self, score: float) -> str:
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
        for category, keywords in self.category_map.items():
            if any(k in text_lower for k in keywords):
                return category
        return "general"
