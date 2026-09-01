import CommonCrypto
import Foundation

enum PBKDF2 {
   /// PBKDF2-HMAC-SHA256, Parameter wie Android (120 000 Iterationen, 256-Bit-Schlüssel).
    static func deriveKey(password: String, salt: Data, iterations: UInt32 = 120_000, keyLength: Int = 32) -> Data? {
        var derived = Data(count: keyLength)
        let status: Int32 = derived.withUnsafeMutableBytes { derivedBuffer in
            salt.withUnsafeBytes { saltBuffer in
                password.withCString { passwordPointer in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordPointer, password.utf8.count,
                        saltBuffer.bindMemory(to: UInt8.self).baseAddress, salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), iterations,
                        derivedBuffer.bindMemory(to: UInt8.self).baseAddress, keyLength
                    )
                }
            }
        }
        return status == kCCSuccess ? derived : nil
    }

    static func randomSalt(length: Int = 16) -> Data {
        var bytes = [UInt8](repeating: 0, count: length)
        let result = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        precondition(result == errSecSuccess, "SecRandomCopyBytes fehlgeschlagen")
        return Data(bytes)
    }
}
