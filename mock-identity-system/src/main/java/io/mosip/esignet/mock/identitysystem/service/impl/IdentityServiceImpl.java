/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mock.identitysystem.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import io.mosip.esignet.mock.identitysystem.dto.VerifiedClaimRequestDto;
import io.mosip.esignet.mock.identitysystem.entity.VerifiedClaim;
import io.mosip.esignet.mock.identitysystem.repository.VerifiedClaimRepository;
import io.mosip.esignet.mock.identitysystem.util.HelperUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.mock.identitysystem.dto.IdentityData;
import io.mosip.esignet.mock.identitysystem.entity.MockIdentity;
import io.mosip.esignet.mock.identitysystem.exception.MockIdentityException;
import io.mosip.esignet.mock.identitysystem.repository.IdentityRepository;
import io.mosip.esignet.mock.identitysystem.service.IdentityService;
import io.mosip.esignet.mock.identitysystem.util.ErrorConstants;

import static io.mosip.esignet.mock.identitysystem.util.HelperUtil.ALGO_SHA3_256;

@Service
public class IdentityServiceImpl implements IdentityService {

	@Value("${mosip.mock.ida.kba.default.field-language}")
	private String fieldLang;
	
	@Autowired
	ObjectMapper objectmapper;

	@Autowired
	IdentityRepository identityRepository;

	@Autowired
	VerifiedClaimRepository verifiedClaimRepository;

	@Override
	public void addIdentity(IdentityData identityData) throws MockIdentityException {
		if (identityRepository.findById(identityData.getIndividualId()).isPresent()) {
			throw new MockIdentityException(ErrorConstants.DUPLICATE_INDIVIDUAL_ID);
		}
		MockIdentity mockIdentity = new MockIdentity();
		try {
			mockIdentity.setIdentityJson(objectmapper.writeValueAsString(identityData));
		} catch (JsonProcessingException e) {
			throw new MockIdentityException(ErrorConstants.JSON_PROCESSING_ERROR);
		}
		mockIdentity.setIndividualId(identityData.getIndividualId());
		identityRepository.save(mockIdentity);
	}

	@Override
	public IdentityData getIdentity(String individualId) throws MockIdentityException {
		Optional<MockIdentity> mockIdentity = identityRepository.findById(individualId);
		if (!mockIdentity.isPresent()) {
			throw new MockIdentityException(ErrorConstants.INVALID_INDIVIDUAL_ID);
		}
		IdentityData identityData = new IdentityData();
		try {
			identityData = objectmapper.readValue(mockIdentity.get().getIdentityJson(), IdentityData.class);
		} catch (JsonProcessingException e) {
			throw new MockIdentityException(ErrorConstants.JSON_PROCESSING_ERROR);
		}
		return identityData;
	}

	@Override
	public JsonNode getIdentityV2(String individualId) throws MockIdentityException {
		Optional<MockIdentity> mockIdentity = identityRepository.findById(individualId);
		if (!mockIdentity.isPresent()) {
			throw new MockIdentityException(ErrorConstants.INVALID_INDIVIDUAL_ID);
		}
		try {
			return objectmapper.readTree(mockIdentity.get().getIdentityJson());
		} catch (JsonProcessingException e) {
			throw new MockIdentityException(ErrorConstants.JSON_PROCESSING_ERROR);
		}
	}



	@Override
	public void addVerifiedClaim(VerifiedClaimRequestDto verifiedClaimRequestDto) throws MockIdentityException {
		VerifiedClaim verifiedClaim =null;
		JsonNode identity = getIdentityV2(verifiedClaimRequestDto.getIndividualId());
		Object fieldValue= HelperUtil.getIdentityDataValue(identity, verifiedClaimRequestDto.getClaim(),fieldLang);
		if(fieldValue==null){
			throw new MockIdentityException(ErrorConstants.INVALID_CLAIM);
		}
		String idHash= HelperUtil.generateB64EncodedHash(ALGO_SHA3_256, verifiedClaimRequestDto.getIndividualId()+ verifiedClaimRequestDto.getTrustFramework().toLowerCase()+ verifiedClaimRequestDto.getClaim());
		Optional<VerifiedClaim> verifiedClaimOptional=verifiedClaimRepository.findById(idHash);
		if(verifiedClaimOptional.isPresent()){
			throw new MockIdentityException(ErrorConstants.CLAIM_ALREADY_EXISTS);
		}
		verifiedClaim = new VerifiedClaim();
		verifiedClaim.setId(idHash);
		verifiedClaim.setClaim(verifiedClaimRequestDto.getClaim());
		verifiedClaim.setIndividualId(verifiedClaimRequestDto.getIndividualId());
		verifiedClaim.setVerifiedDateTime(verifiedClaimRequestDto.getVerifiedDateTime());
		verifiedClaim.setTrustFramework(verifiedClaimRequestDto.getTrustFramework());
		verifiedClaim.setCrDateTime(LocalDateTime.now());
		verifiedClaim.setActive(true);
		verifiedClaimRepository.save(verifiedClaim);
	}

}
