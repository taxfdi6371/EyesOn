package com.backend.eyeson.controller;

import com.backend.eyeson.dto.*;
import com.backend.eyeson.entity.ComplaintsEntity;
import com.backend.eyeson.entity.UserEntity;
import com.backend.eyeson.repository.CompRepository;
import com.backend.eyeson.repository.UserRepository;
import com.backend.eyeson.service.CompService;
import com.backend.eyeson.util.ResponseFrame;
import com.backend.eyeson.util.ReverseGeocoding;
import com.google.api.Page;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.google.api.client.json.JsonFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;


@RestController
@RequestMapping("/complaints")
@RequiredArgsConstructor
@Api(tags   = "민원 컨트롤러")
public class ComplaintsController {
    private final CompService compService;

    @ApiParam(value = "민원 등록")
    @PostMapping(value = "/register", consumes = {MediaType.APPLICATION_JSON_VALUE , MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> registerCom(@RequestPart(value = "params") RequestCompDto params, @RequestPart(value="file", required = false) MultipartFile multipartFile) throws Exception{
        boolean result = compService.registerCom(params, multipartFile);

        return new ResponseEntity<>(ResponseFrame.of(result, "민원 등록 성공"), HttpStatus.OK);
    }

    @ApiParam(value = "민원 조회 Flag| 0:전체 1:엔젤 2:블라인드")
    @GetMapping(value = "/list/{flag}")
    public ResponseEntity<?> getList(@PageableDefault Pageable pageable, @PathVariable int flag) throws Exception{
        switch (flag){
            case 0: {return new ResponseEntity<>(ResponseFrame.of(compService.listAll(pageable), "신청 민원 전체 조회"), HttpStatus.OK);}
            case 1: {return new ResponseEntity<>(ResponseFrame.of(compService.listAngel(pageable), "엔젤 민원 조회"), HttpStatus.OK);}
            case 2: {return new ResponseEntity<>(ResponseFrame.of(compService.listBlind(pageable), "블라인드 신청 민원 조회"), HttpStatus.OK);}
        }

        return new ResponseEntity<>(ResponseFrame.of(HttpStatus.BAD_REQUEST, "조회 실패"), HttpStatus.OK);
    }

    @ApiParam(value = "신청 민원 전체 조회")
    @GetMapping(value = "/list")
    public ResponseEntity<?> listAll(@PageableDefault Pageable pageable) throws Exception{
        PagingResult result = compService.listAll(pageable);

        return new ResponseEntity<>(ResponseFrame.of(result, "신청 민원 전체 조회"), HttpStatus.OK);
    }

    @ApiParam(value = "엔젤: 내가 처리한 민원 조회")
    @GetMapping(value = "/list/angel")
    public ResponseEntity<?> listAngel(@PageableDefault Pageable pageable) throws Exception{
        PagingResult result = compService.listAngel(pageable);

        return new ResponseEntity<>(ResponseFrame.of(result, "민원 조회"), HttpStatus.OK);
    }

    @ApiParam(value = "시각장애인: 내가 신청한 민원 조회")
    @GetMapping(value = "/list/blind")
    public ResponseEntity<?> listBlind(@PageableDefault Pageable pageable) throws Exception{
        PagingResult result = compService.listBlind(pageable);

        return new ResponseEntity<>(ResponseFrame.of(result, "민원 조회"), HttpStatus.OK);
    }

    @ApiParam(value = "민원 상세 조회")
    @GetMapping(value = "/{complaintsSeq}")
    public ResponseEntity<?> detailCom(@PathVariable("complaintsSeq") long compSeq) throws Exception{
        ResponseCompDto complaintsDto = compService.detailCom(compSeq);

        return new ResponseEntity<>(ResponseFrame.of(complaintsDto, "민원 상세 조회"), HttpStatus.OK);
    }

    @ApiParam(value = "민원 반환")
    @PutMapping(value = "/return")
    public ResponseEntity<?> returnCom(@RequestBody RequestCompDto params) throws Exception{
        ResponseCompDto result = compService.returnCom(params);

        return new ResponseEntity<>(ResponseFrame.of(result, "반환 완료"), HttpStatus.OK);
    }

    @ApiParam(value = "민원 접수 완료")
    @PutMapping(value = "/submit")
    public ResponseEntity<?> submitCom(@RequestBody RequestCompDto params) throws Exception{
        ResponseCompDto result = compService.submitCom(params);

        return new ResponseEntity<>(ResponseFrame.of(result, "민원 접수 완료"), HttpStatus.OK);
    }

    @ApiParam(value = "민원 처리 완료")
    @PutMapping(value = "/complete")
    public ResponseEntity<?> completeCom(@RequestBody RequestCompDto params) throws Exception{
        ResponseCompDto result = compService.completeCom(params);

        return new ResponseEntity<>(ResponseFrame.of(result, "민원 처리 완료"), HttpStatus.OK);
    }

    @ApiParam(value = "모든 엔젤들이 처리한 민원 횟수")
    @GetMapping(value= "/entire")
    public Integer entire() throws IOException {
        Integer result = compService.entire();
        return result;
    }

//    @ApiParam(value = "TEST API")
//    @GetMapping(value= "/addressTest")
//    public String test(@RequestParam String address) throws IOException {
//        String result = ReverseGeocoding.getAddress(address);
//        return result;
//    }

}
